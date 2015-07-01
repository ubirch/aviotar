/*
 * Copyright 2015 ubirch GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package ubirch.aviotar

import java.net.URI
import java.text.SimpleDateFormat
import java.util.{Date, Locale}

import akka.actor._
import net.liftweb.json.JValue
import net.liftweb.json.JsonDSL._
import net.liftweb.json.JsonParser.parse
import net.liftweb.json.Serialization.write
import ubirch.mqtt.MQTTClient
import ubirch.mqtt.MQTTClient.{Configuration, Message, Publish, Subscribe}
import ubirch.timeseries.Storage
import ubirch.timeseries.components.ElasticSearchStorageComponent

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Random

/**
 * Add description.
 *
 * @author Matthias L. Jugel
 */
object Main extends App {
  implicit val formats = net.liftweb.json.DefaultFormats

  val system = ActorSystem("aviotar")
  val mqttConfig = new Configuration(clientId = Some("aviotar-test"))
  val mqttClient = system.actorOf(Props(new MQTTClient(new URI("tcp://localhost:1883"), mqttConfig)))

  object SensorStorage extends Storage[JValue] with ElasticSearchStorageComponent {
    override val uri: URI = new URI("http://localhost:9200/leo/")
  }

  class SubscriberActor extends Actor with ActorLogging {
    override def receive: Receive = {
      case Subscribe(topic, _, qos) =>
        log.info(s"subscribed to $topic (QoS: $qos)")
      case message: Message =>
        log.info(message.toString)
        try {
          val json: JValue = parse(new String(message.payload, "UTF-8"))
          SensorStorage.store(message.topic, json)
            .map(r => log.info(s"== ${r.values} =="))
            .onFailure { case e: Exception => log.error(e.getMessage) }
        }
        catch {
          case e: Exception => log.error(e.getMessage)
        }
      case unknown =>
        log.info(s"unknown message received: $unknown")
    }
  }

  val subscriberActor = system.actorOf(Props(classOf[SubscriberActor]))

  mqttClient ! Subscribe("/sensors/+", subscriberActor)

  mqttClient ! Publish("/sensors/sensor-1", "TEST".getBytes("UTF-8"))


  //  val storage = new ElasticSearchStorage(new URI("http://localhost:9200/leo"))
  //  broker ! Subscribe("/sensors/#", system.actorOf(Props(new MQTTStorageActor(storage))))
  //
  //  val publisher = new MqttClient("tcp://localhost:1883", "storage-client-publisher")
  //  publisher.connectWithResult(new MqttConnectOptions)
  val df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.ENGLISH)
  (1 to 10).foreach { i =>
    val payload: JValue = ("lat" -> 51.9310727) ~ ("lon" -> 12.8205444) ~ ("acc" -> 3312.0) ~ ("batt" -> 69) ~
      ("@timestamp" -> df.format(new Date()))
    mqttClient ! Publish(s"/sensors/sensor-${Random.nextInt(10)}", write(payload).getBytes("UTF-8"))
  }

}
