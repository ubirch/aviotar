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

package ubirch.aviotar.actors

import java.net.URI

import akka.actor.{Actor, ActorLogging}
import net.liftweb.json.JsonDSL._
import net.liftweb.json._
import ubirch.mqtt.MQTTClient.{Message, Subscribe}
import ubirch.timeseries.Storage
import ubirch.timeseries.components.ElasticSearchStorageComponent

import scala.concurrent.ExecutionContext.Implicits.global

/**
 * ElasticSearch sink that stores JSON data.
 *
 * @author Matthias L. Jugel
 */
class ElasticSearchSink(base: String, index: String, tsField: String = "@timestamp") extends Actor with ActorLogging {

  private object SensorStorage extends Storage[JValue] with ElasticSearchStorageComponent {
    override val timestamp: String = tsField
    override val uri: URI = new URI(s"$base/$index/")
  }


  @throws[Exception](classOf[Exception])
  override def preStart(): Unit = {
    log.info(s"url: $base/$index")
    log.info(s"timestamp field: $tsField")
    super.preStart()
  }

  override def receive: Receive = {
    case Subscribe(topic, actor, qos) =>
      if (actor != self) log.error(s"subscribed wrong actor ($actor) to '$topic'")
      else log.info(s"subscribed to $topic (QoS: $qos)")

    case message: Message =>
      try {
        val storeType = message.topic.split("/").filter(_ != "").mkString(".")
        val topic: JValue = "_topic" -> message.topic
        val json: JValue = parse(new String(message.payload, "UTF-8")).merge(topic)

        SensorStorage.store(storeType, json).onFailure {
          case e: Exception => log.error(e.getMessage, e)
        }
      } catch {
        case e: Exception => log.error(s"receive: ${e.getMessage}", e)
      }
  }
}