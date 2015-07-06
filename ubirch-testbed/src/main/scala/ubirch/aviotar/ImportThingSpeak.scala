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
import java.math.BigInteger
import java.net.URI
import java.text.SimpleDateFormat
import java.util.TimeZone

import akka.actor._
import com.typesafe.scalalogging.LazyLogging
import net.liftweb.json.JsonDSL._
import net.liftweb.json.Serialization.write
import net.liftweb.json.{Extraction, JObject, JValue, parse}
import ubirch.aviotar.actors.ElasticSearchSink
import ubirch.mqtt.MQTTClient
import ubirch.mqtt.MQTTClient.{Configuration, Publish, Subscribe}

import scala.io.Source
import scala.util.Try

/**
 * Import thingspeak feed files. A directory containing json files is expected as command line argument.
 * To create the dumps use the following script:
 *
 * {{{
 * #! /bin/sh
 * for i in `seq 1 43`; do
 *   curl "http://api.ubirch.com/channels/$i/feed.json?start=2014-01-01%2000:00:00&end=2015-12-31%2000:00:00&key=XXXXXX" > $i.json
 * done
 * }}}
 *
 *
 * @author Matthias L. Jugel
 */
object ImportThingSpeak extends App with LazyLogging {
  // TODO: use a URL and an API key to directly access a thingspeak server.

  implicit val formats = net.liftweb.json.DefaultFormats

  val system = ActorSystem("aviotar")
  val mqttConfig = new Configuration(clientId = Some("aviotar-test"))
  val mqtt = system.actorOf(Props(new MQTTClient(new URI("tcp://localhost:1883"), mqttConfig)))

  mqtt ! Subscribe("/sensors/#", system.actorOf(Props(new ElasticSearchSink("http://localhost:9200", "sensors", "@ts"))), 2)

  val dir = new File(args(0))
  if (dir.exists) {
    var thingspeakFeeds = new File(args(0)).listFiles(new FilenameFilter {
      override def accept(dir: File, name: String): Boolean = name.endsWith(".json")
    })

    val publishFormat = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ssZ")
    publishFormat.setTimeZone(TimeZone.getTimeZone("UTC"))

    val feedFormat = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss'Z'")
    feedFormat.setTimeZone(TimeZone.getTimeZone("UTC"))

    for (feed <- thingspeakFeeds) {
      val jsonString = Source.fromFile(feed, "UTF-8").mkString
      try {
        val json = parse(jsonString).asInstanceOf[JObject]
        val channel: Map[String, _] = (json \ "channel").values.asInstanceOf[Map[String, Any]]
        val id = channel("id") + "/" + channel("name")
        println(s"$id: ${channel.getOrElse("description", "???")}")

        (json \ "feeds").extract[Array[JObject]].foreach {
          entry =>
            val timestamp = feedFormat.parse((entry \ "created_at").extract[String])
            val fields = (1 to 8).collect {
              case i if (entry \ s"field$i").toOpt.isDefined && (entry \ s"field$i").extract[String] != null =>
                val fieldId: String = s"field$i"
                // fix some obvious data errors
                val s = (entry \ fieldId).extract[String].replaceAll("token=tbd", "")
                // extract the actual value (string => Int -> Double -> json -> string)
                val value = Try(new BigInteger(s).longValue()) orElse
                  Try(new java.math.BigDecimal(s).doubleValue()) orElse
                  Try(parse(s).values) getOrElse s
                // storage data using the actual field name (which should be lowercase)
                channel(fieldId).toString.toLowerCase -> value
            }
            val data = Extraction.decompose(fields.toMap).merge("@ts" -> publishFormat.format(timestamp): JValue)
            mqtt ! Publish(s"/sensors/$id", write(data).getBytes("UTF-8"))
        }
      } catch {
        case e: Exception => logger.error(jsonString)
      }
    }
  } else {
    println(s"$dir: does not exist")
  }
}
