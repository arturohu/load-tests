package simulations

import java.util.UUID

import io.gatling.core.Predef._
import io.gatling.core.action.builder.ActionBuilder
import io.gatling.core.structure.ScenarioBuilder
import io.gatling.http.Predef._
import scalaj.http.Http

import scala.util.Random

/**
 * Base class for all Simulations. Provides a set of common configurations and methods to retrieve different actions that can be used for building
 * scenarios.
 */
abstract class FiwareLDBaseSimulation extends Simulation {
  val testConfig = TestConfiguration()

  val entitiesToSimulate = testConfig.numEntities
  val baseUrl = testConfig.baseUrl
  val numberOfUpdatesToSimulate = testConfig.numUpdates
  val updateDelay = testConfig.updateDelay
  val entitiesToPrefill = testConfig.numPrefillEntities


  val httpConf = http.baseUrl(baseUrl)

  val prefillEnitiyIdList: List[UUID] = Stream.fill(entitiesToPrefill)(UUID.randomUUID()).toList

  before {
    beforeScenario();
    if (entitiesToPrefill > 0) {
      println("++++++++++++++++++++++++++++++++++++ EXECUTE BEFORE ++++++++++++++++++++++++++++++++++")
      println("Prefill db with " + entitiesToPrefill + " entities.")

      val batches: Int = (entitiesToPrefill / 100).ceil.toInt
      println("Will create " + batches + " batches.")
      for (a <- 0 to batches - 1) {
        println("Create batch from " + a * 100 + " to " + (a + 1) * 100)
        val response = Http(baseUrl + "entityOperations/create").header("Content-Type", "application/ld+json").postData(getUpdateBody(a * 100, (a + 1) * 100, prefillEnitiyIdList)).timeout(10000, 60000).asString

        if (response.code != 200) {
          throw new RuntimeException("Was not able to prefill the database. Response: " + response)
        }
      }
    }
  }

  after {
    afterScenario();
    if (entitiesToPrefill > 0) {
      println("++++++++++++++++++++++++++++++++++++ EXECUTE AFTER +++++++++++++++++++++++++++++++++++")
      println("Delete " + entitiesToPrefill + " prefilled entities.")
      val batches: Int = (entitiesToPrefill / 100).ceil.toInt
      for (a <- 0 to batches - 1) {
        println("Status: " + Http(baseUrl + "entityOperations/delete").header("Content-Type", "application/ld+json").postData(getDeleteBody(a * 100, (a + 1) * 100, prefillEnitiyIdList)).timeout(10000, 20000).asString.code)
      }
    }
  }

  val scn = getScenario()


  // simulation will simulate lifecycle of one entity, at once will care about running them in parallel
  setUp(scn.inject(atOnceUsers(getParallelRuns()))).protocols(httpConf)

  /*
   * must be implemented by the subclasses and should contain the actual scenario
   */
  def getScenario(): ScenarioBuilder

  /*
   * must be implemented by the subclasses and should retrieve the actual  number of parallel connections
   */
  def getParallelRuns(): Int

  /**
   * Method to override if something specific should be done before the scenario
   */
  def beforeScenario() = {};

  /**
   * Method to override if something specific should be done after the scenario
   */
  def afterScenario() = {};

  /*
   * creates a single entity, id needs to be fed via session-attribute 'entityId'
   */
  def createEntityAction(): ActionBuilder = {
    http("create entity")
      .post("/entities")
      .body(StringBody((s: Session) => getEntityString(s("entityId").as[String])))
      .header("Content-Type", "application/ld+json")
  }

  /*
  * creates a single entity with a timestamp, id needs to be fed via session-attribute 'entityId'
  */
  def createTimedEntityAction(): ActionBuilder = {
    http("create entity")
      .post("/entities")
      .body(StringBody((s: Session) => getNotificationTestEntity(s("entityId").as[String])))
      .header("Content-Type", "application/ld+json")
  }

  /*
   * updates an attribute of a single entity, id needs to be fed via session-attribute 'entityId'
   */
  def updateEntityAction(attributeToUpdate: String): ActionBuilder = {
    http("update temperature")
      .post((s: Session) => "/entities/urn:ngsi-ld:store:" + s("entityId").as[String] + "/attrs")
      .body(StringBody((s: Session) => """{"""" + attributeToUpdate + """":{"type":"Property", "value":""" + Random.nextFloat() * 10 + """}, "sent-time": {"type":"Property", "value": """ + System.currentTimeMillis() +"""}, "@context": "https://fiware.github.io/data-models/context.jsonld"}""".stripMargin))
      .header("Content-Type", "application/ld+json")
  }

  /*
   * updates an attribute of a single entity, id needs to be fed via session-attribute 'entityId'
   */
  def updateTimedEntityAction(): ActionBuilder = {
    http("update humidity")
      .post((s: Session) => "/entities/urn:ngsi-ld:timed-entity:" + s("entityId").as[String] + "/attrs")
      .body(StringBody((s: Session) => """{"humidity":{"type":"Property", "value":""" + Random.nextFloat() * 10 + """}, "sent-time": {"type":"Property", "value": """ + System.currentTimeMillis() +"""}, "@context": "https://fiware.github.io/data-models/context.jsonld"}""".stripMargin))
      .header("Content-Type", "application/ld+json")
  }

  /*
   * deletes a single entity, id needs to be fed via session-attribute 'entityId'
   */
  def deleteEntityAction(): ActionBuilder = {
    http("delete entity")
      .delete((s: Session) => "/entities/urn:ngsi-ld:store:" + s("entityId").as[String])
  }

  /*
   * deletes a single entity, id needs to be fed via session-attribute 'entityId'
   */
  def deleteTimedEntityAction(): ActionBuilder = {
    http("delete entity")
      .delete((s: Session) => "/entities/urn:ngsi-ld:timed-entity:" + s("entityId").as[String])
  }


  /*
   * Create multiple entities as a batch. The number of the current batch should be feed as a session attribute
   */
  def batchCreateEntitiesAction(batchSize: Int, idList: List[UUID]): ActionBuilder = {
    http("create batch of entities")
      .post("/entityOperations/create")
      .body(StringBody((s: Session) => getUpdateBody(s("batchNumber").as[Int] * batchSize, (s("batchNumber").as[Int] + 1) * batchSize, idList)))
      .header("Content-Type", "application/ld+json")
  }

  /*
 * Create multiple entities as a batch. The number of the current batch should be feed as a session attribute
 */
  def batchCreateEntitiesActionFromStringList(batchSize: Int, idList: List[String]): ActionBuilder = {
    http("create batch of entities")
      .post("/entityOperations/create")
      .body(StringBody((s: Session) => getUpdateBodyFromStringList(s("batchNumber").as[Int] * batchSize, (s("batchNumber").as[Int] + 1) * batchSize, idList)))
      .header("Content-Type", "application/ld+json")
  }


  /*
   * Update multiple entities as a batch. The number of the current batch should be feed as a session attribute
   */
  def batchUpdateEntitiesAction(batchSize: Int, idList: List[UUID]): ActionBuilder = {
    http("update batch of entities")
      .post("/entityOperations/update")
      .body(StringBody((s: Session) => getUpdateBody(s("batchNumber").as[Int] * batchSize, (s("batchNumber").as[Int] + 1) * batchSize, idList)))
      .header("Content-Type", "application/ld+json")
  }

  /*
   * Delete multiple entities as a batch. The number of the current batch should be feed as a session attribute
   */
  def batchDeleteEntities(batchSize: Int, idList: List[UUID]): ActionBuilder = {
    http("delete batch of entities")
      .post("/entityOperations/delete")
      .body(StringBody((s: Session) => getDeleteBody(s("batchNumber").as[Int] * batchSize, (s("batchNumber").as[Int] + 1) * batchSize, idList)))
      .header("Content-Type", "application/ld+json")
  }

  /**
   * Get a single entity. Id is expected as a session attribute
   */
  def singleEntityGetAction(): ActionBuilder = {
    http(" get a single entity")
      .get((s: Session) => "/entities/urn:ngsi-ld:store:" + s("entityId").as[String])
      .header("Content-Type", "application/ld+json")
  }

  def getEntityString(entityId: String): String = {
    """{"type":"store", "id":"urn:ngsi-ld:store:""" + entityId +
      """",
       "temperature": {
          "type": "Property",
          "value": """ + Random.nextInt() +
      """
          },
       "humidity": {
          "type": "Property",
          "value": """ + Random.nextFloat() +
      """
        },
        "sent-time": {
          "type": "Property",
          "value": """ +System.currentTimeMillis() + """
          },
       "open": {
         "type": "Property",
         "value": "true"
       },
       "owner": {
        "type": "Relationship",
        "object": "urn:ngsi-ld:owner:random-owner"
       },
       "@context": "https://fiware.github.io/data-models/context.jsonld"
       }"""
  }

  def getRandomShardKey(): String = {
    Random.nextInt(2).toString
  }

  def getNotificationTestEntity(entityId: String): String = {
    """{"type":"timed-entity", "id":"urn:ngsi-ld:timed-entity:""" + entityId +
      """",
       "sent-time": {
          "type": "Property",
          "value": """ + System.currentTimeMillis() +"""
          },
       "humidity": {
          "type": "Property",
          "value": """ + Random.nextFloat() +
      """
        },
       "@context": "https://fiware.github.io/data-models/context.jsonld"
       }"""
  }

  def getDeleteBody(startPos: Int, endPos: Int, idList: List[UUID]): String = {
    val idIterator: Iterator[UUID] = idList.slice(startPos, endPos).iterator
    val deleteBodyBuilder: StringBuilder = StringBuilder.newBuilder
    deleteBodyBuilder.append("""[ """)
    deleteBodyBuilder.append(""""urn:ngsi-ld:store:""" + idIterator.next().toString + """"""")
    while (idIterator.hasNext) {
      deleteBodyBuilder.append(""","urn:ngsi-ld:store:""" + idIterator.next().toString + """"""")
    }

    deleteBodyBuilder.append("]").toString()
  }

  def getUpdateBodyFromStringList(startPos: Int, endPos: Int, idList: List[String]): String = {
    val idIterator: Iterator[String] = idList.slice(startPos, endPos).iterator
    val updateBodyBuilder: StringBuilder = StringBuilder.newBuilder

    updateBodyBuilder.append(
      """
      [
      """)

    updateBodyBuilder.append(getEntityString(idIterator.next()))

    while (idIterator.hasNext) {
      updateBodyBuilder.append("," + getEntityString(idIterator.next()))
    }

    updateBodyBuilder.append("]").toString()
  }

  def getUpdateBody(startPos: Int, endPos: Int, idList: List[UUID]): String = {
    val idIterator: Iterator[UUID] = idList.slice(startPos, endPos).iterator
    val updateBodyBuilder: StringBuilder = StringBuilder.newBuilder

    updateBodyBuilder.append(
      """
      [
      """)

    updateBodyBuilder.append(getEntityString(idIterator.next().toString))

    while (idIterator.hasNext) {
      updateBodyBuilder.append("," + getEntityString(idIterator.next().toString))
    }

    updateBodyBuilder.append("]").toString()
  }

  def getTypeSubscriptionAction(serverUrl: String, entitiyType: String): String = {
    """{
             "id" : "urn:ngsi-ld:Subscription:""" + entitiyType + """",
             "type": "Subscription",
             "@context": "https://fiware.github.io/data-models/context.jsonld",
             "entities": [
               {
              "type": """" + entitiyType + """"
           }
        ],
       "watchedAttributes": [ "humidity" ],
       "q": "humidity>0",
       "notification": {
         "endpoint": {
            "uri": """" + serverUrl +
      """"
          }
       }
      }"""
  }

  def getEverythingSubscriptionAction(serverUrl: String): String = {
    """{
             "id" : "urn:ngsi-ld:Subscription:everything",
             "type": "Subscription",
             "@context": "https://fiware.github.io/data-models/context.jsonld",
             "entities": [
               {
              "idPattern": ".*"
           }
        ],
       "notification": {
         "endpoint": {
            "uri": """" + serverUrl +
      """"
          }
       }
      }"""
  }
  /*
   * Create a subscription. The id of the entity to  subscribe to needs to be fed as a session attribute.
   */
  def createSubscriptionAction(serverUrl: String): ActionBuilder = {
    http("create subscriptions")
      .post((s: Session) => "/subscriptions")
      .body(StringBody((s: Session) =>
        """{
            "type": "Subscription",
            "@context": "https://fiware.github.io/data-models/context.jsonld",
            "entities": [
              {
                  "id": "urn:ngsi-ld:store:""" + s("entityId").as[String] +
          """",
                  "type": "store"
               }
            ],
           "watchedAttributes": [ "temperature" ],
           "q": "temperature>0",
           "notification": {
             "attributes": [
                "temperature",
                "humidity",
                "sent-time"
             ],
             "endpoint": {
                "uri": """" + serverUrl +
          """"
             }
           }
          }"""))
      .header("Content-Type", "application/ld+json")
  }
}
