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

import java.io._
import java.net.URI

import akka.actor._
import com.typesafe.scalalogging.LazyLogging
import net.liftweb.json.JValue
import net.liftweb.json.JsonAST.JField
import net.liftweb.json.JsonParser.parse
import ubirch.aviotar.actors.ElasticSearchSink
import ubirch.mqtt.MQTTClient
import ubirch.mqtt.MQTTClient.{Configuration, Subscribe}


/**
 *
 * @author Matthias L. Jugel
 */
object Main extends App with LazyLogging {
  implicit val formats = net.liftweb.json.DefaultFormats
  val system = ActorSystem("aviotar")

  try {
    val options = nextOption(args.toList)
    val config = readConfig(options.get('config))

    val mqttConfig = buildMQTTConfiguration(config \ "mqtt")
    logger.info(s"MQTT client ID: ${mqttConfig.clientId}")
    val mqtt = system.actorOf(Props(new MQTTClient(new URI("tcp://localhost:1883"), mqttConfig)))

    (config \ "subscriber").children.foreach {
      case JField(topic, sub) =>
        val qos: Int = (sub \ "qos").extractOrElse(0)
        val actor = (sub \ "type").extractOpt[String] match {
          case Some("ElasticSearchSink") =>
            val url = (sub \ "url").extract[String]
            val index = (sub \ "index").extract[String]
            val ts = (sub \ "timestamp").extract[String]
            system.actorOf(Props(new ElasticSearchSink(url, index, ts)), s"es-storage-$index")
          case Some(unknown) =>
            throw new IllegalArgumentException(s"subscriber $unknown not available")
          case None =>
            throw new IllegalArgumentException("no subscriber found")
        }
        mqtt ! Subscribe(topic, actor, qos)
      case unknown: JValue =>
        throw new IllegalArgumentException(s"unknown subscriber: $unknown")
    }
  } catch {
    case e: IllegalArgumentException =>
      System.err.println(s"configuration error: ${e.getMessage}")
      System.exit(1)
    case e: Exception =>
      System.err.println(s"can't start: ${e.getMessage}")
      e.printStackTrace()
      System.exit(1)
  }

  def buildMQTTConfiguration(config: JValue): Configuration = {
    Configuration(
      userName = (config \ "user").extractOpt[String],
      password = (config \ "password").extractOpt[String],
      clientId = (config \ "clientId").extractOpt[String],
      cleanSession = (config \ "cleanSession").extractOpt[Boolean],
      connectionTimeout = (config \ "connectionTimeout").extractOpt[Int],
      keepAliveInterval = (config \ "keepAliveInterval").extractOpt[Int],
      reconnectTriesMax = (config \ "reconnectTriesMax").extractOrElse(10)
      // TODO: add some of the other options
    )

  }
  def nextOption(list: List[String], map: Map[Symbol, Any] = Map()): Map[Symbol, Any] = {
    def isSwitch(s: String) = s.charAt(0) == '-'

    list match {
      case Nil => map
      case "-c" :: value :: tail =>
        nextOption(tail, map ++ Map('config -> value))
      case option :: tail =>
        throw new IllegalArgumentException(option)
    }
  }

  def readConfig(file: Option[Any]): JValue = file match {
    case Some(f) =>
      val configFile = new File(f.toString)
      if(configFile.exists()) parse(new FileReader(configFile))
      else throw new IllegalArgumentException(s"configuration file $f does not exist")
    case None =>
      parse(new InputStreamReader(getClass.getResourceAsStream("/ubirch-server.json")))
  }
}
