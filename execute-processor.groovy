import org.apache.nifi.processor.io.StreamCallback
import java.nio.charset.StandardCharsets
import redis.clients.jedis.Jedis
import java.time.LocalDate
import java.time.format.DateTimeFormatter

def flowFile = session.get()
if (!flowFile) {
    log.error("No flowfile received!")
    return
}

def redisHost = "localhost"
def redisPort = 6379

def lat = flowFile.getAttribute('lat')
def lon = flowFile.getAttribute('lon')
def deltaStr = flowFile.getAttribute('delta')
def delta = (deltaStr ? deltaStr.toDouble() : 3.5)
def tempsKey = "temps:${lat}:${lon}"
def sentKey = "alertSent:${lat}:${lon}"

log.info("==== Custom ExecuteScript START ====")

def currentTemp = flowFile.getAttribute('currentTemp')
log.info("Current temp attribute: " + currentTemp)
if (!currentTemp) {
    log.error("currentTemp attribute missing on flowfile! Attributes: " + flowFile.getAttributes())
    session.transfer(flowFile, REL_FAILURE)
    return
}

def jedis = new Jedis(redisHost, redisPort)
try {
    log.info("Pushing temp " + currentTemp + " to Redis list " + tempsKey)
    // Push current temp to Redis list and trim to last 5
    jedis.lpush(tempsKey, currentTemp)
    jedis.ltrim(tempsKey, 0, 4)

    // Get last 5 temps
    def temps = jedis.lrange(tempsKey, 0, 4)
    log.info("Last 5 temps from Redis: " + temps)

    if (temps.size() < 5) {
        log.info("Not enough measurements yet (" + temps.size() + "). Need 5 for sliding window. No alert will be sent.")
        flowFile = session.putAttribute(flowFile, 'avgTemp', "not-calculated")
        flowFile = session.putAttribute(flowFile, 'alert', "false")
        session.transfer(flowFile, REL_SUCCESS)
        return
    }

    def tempsDouble = temps.collect { it.toDouble() }
    double avg = tempsDouble.sum() / tempsDouble.size()
    log.info("Calculated average: " + avg)

    // Check if alert was already sent today
    def today = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
    def lastSent = jedis.get(sentKey)
    log.info("Alert last sent: " + lastSent + ", Today: " + today)

    boolean alert = (avg >= delta) && (today != lastSent)
    log.info("Should alert? " + alert)

    if (alert) {
        // Mark alert sent for today, expire at midnight
        log.info("Setting alertSent flag in Redis: " + sentKey)
        jedis.set(sentKey, today)
        def tomorrow = LocalDate.now().plusDays(1).atStartOfDay().toEpochSecond(java.time.ZoneOffset.UTC)
        jedis.expireAt(sentKey, tomorrow)
    }

    // Add useful attributes
    flowFile = session.putAttribute(flowFile, 'avgTemp', avg.toString())
    flowFile = session.putAttribute(flowFile, 'alert', alert.toString())

    log.info("Added avgTemp and alert attributes to flowfile.")

    session.transfer(flowFile, REL_SUCCESS)
} catch (Exception e) {
    log.error("Failed in ExecuteScript Groovy: " + e, e)
    session.transfer(flowFile, REL_FAILURE)
} finally {
    jedis.close()
    log.info("==== Custom ExecuteScript END ====")
}
