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

import java.io.{BufferedReader, InputStreamReader}
import java.math.BigInteger
import java.net.{URI, URL}
import java.text.SimpleDateFormat
import java.util.{TimeZone, Date}

import akka.actor.{Props, Actor, ActorLogging}
import net.liftweb.json.{JValue, Extraction, JObject}
import net.liftweb.json.JsonParser.parse
import net.liftweb.json.JsonDSL._
import net.liftweb.json.Serialization._
import org.apache.http.client.utils.URIBuilder
import ubirch.mqtt.MQTTClient
import ubirch.mqtt.MQTTClient.{Configuration, Publish}

import scala.util.Try


object ThingSpeakImport {

  case class Import(base: String, params: Map[String, String], since: Option[Date] = None)

  final val PARAM_START = "start"
  final val PARAM_DAYS = "days"
  final val PARAM_KEY = "key"
  final val PARAM_LOCATION = "location"
  final val PARAM_METADATA = "metadata"
  final val PARAM_STATUS = "status"
}

/**
 * Add description.
 *
 * @author Matthias L. Jugel
 */
class ThingSpeakImport extends Actor with ActorLogging {

  import ThingSpeakImport._

  override def receive: Receive = {
    case Import(base, params, since) => importFeed(base, params, since)
  }

  implicit val formats = net.liftweb.json.DefaultFormats

  val mqtt = context.actorOf(Props(new MQTTClient(new URI("tcp://pirx.ubirch.com:1883"), new Configuration())))

  val tsFeedDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss'Z'")
  tsFeedDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"))

  private val tsQueryDateFormat = new SimpleDateFormat("yyyy-dd-mm HH:mm:ss")
  tsQueryDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"))

  val esDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ssZ")
  esDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"))

  private def importFeed(base: String, params: Map[String, String], since: Option[Date] = None) {
    val url = new URIBuilder(base)
    params.foreach(p => url.setParameter(p._1, p._2))
    since match {
      case Some(date) => url.setParameter(PARAM_START, tsQueryDateFormat.format(date))
      case None => url.setParameter(PARAM_DAYS, "365")
    }

    val feed = parse(new BufferedReader(new InputStreamReader(url.build.toURL.openStream()))).asInstanceOf[JObject]
    val channel: Map[String, _] = (feed \ "channel").values.asInstanceOf[Map[String, Any]]
    val id = channel("id") + "/" + channel("name")

    (feed \ "feeds").extract[Array[JObject]].foreach {
      entry =>
        val timestamp = tsFeedDateFormat.parse((entry \ "created_at").extract[String])
        val lat = Try(new java.math.BigDecimal((entry \ "latitude").extract[String]).doubleValue()) getOrElse 0.0
        val lon = Try(new java.math.BigDecimal((entry \ "longitude").extract[String]).doubleValue()) getOrElse 0.0

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
        val data = Extraction.decompose(fields.toMap)
          .merge("geo" -> List(lat, lon): JValue)
          .merge("@ts" -> esDateFormat.format(timestamp): JValue)
        mqtt ! Publish(s"/feeds/$id", write(data).getBytes("UTF-8"))
    }
  }


}
