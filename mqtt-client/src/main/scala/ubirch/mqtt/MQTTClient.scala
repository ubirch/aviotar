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

package ubirch.mqtt

import java.net.{URI, URLDecoder, URLEncoder}
import java.util.{Properties, UUID}

import akka.actor._
import org.eclipse.paho.client.mqttv3._

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.{Deadline, FiniteDuration, _}
import scala.language.postfixOps

/**
 * An Akka MQTT client based on the paho-java library.
 *
 * @author Matthias L. Jugel
 *
 *         Rewrite, based on code by Gia Bảo <giabao@github>
 *         https://github.com/giabao/paho-akka
 *
 *         ## Licence
 *         This software is licensed under the Apache 2 license:
 *         http://www.apache.org/licenses/LICENSE-2.0
 */
object MQTTClient {

  case class Publish(topic: String, payload: Array[Byte], qos: Int = 0)

  case class Subscribe(topic: String, actor: ActorRef, qos: Int = 0)

  case class Unsubscribe(topic: String)

  class Message(val topic: String, message: MqttMessage) {
    lazy val payload = message.getPayload
    lazy val qos = message.getQos
    lazy val retained = message.isRetained
    lazy val duplicate = message.isDuplicate

    override def toString: String = s"Message($topic, ${new String(payload, "UTF-8")}, $qos)"
  }

  class Ack(val topics: Set[String], message: MqttMessage) {
    lazy val payload = message.getPayload
    lazy val qos = message.getQos
    lazy val retained = message.isRetained
    lazy val duplicate = message.isDuplicate

    override def toString: String = s"Ack($topics, ${new String(payload, "UTF-8")}, $qos)"
  }

  protected sealed trait ClientState

  protected[mqtt] case object ClientConnected extends ClientState

  protected[mqtt] case object ClientDisconnected extends ClientState

  protected[mqtt] case object Connect

  protected[mqtt] case object Connected

  protected[mqtt] case class Disconnected(throwable: Option[Throwable])

  case class Configuration(userName: Option[String] = None,
                           password: Option[String] = None,
                           clientId: Option[String] = None,
                           cleanSession: Option[Boolean] = None,
                           connectionTimeout: Option[Int] = None,
                           keepAliveInterval: Option[Int] = None,
                           reconnectTriesMax: Int = 10,
                           sslProperties: Properties = null,
                           stashTTL: FiniteDuration = 1 minute,
                           stashSize: Int = 1000)

  protected class TopicActor extends Actor {
    private[this] var subscribers = Set.empty[ActorRef]

    def receive = {
      case msg: Message =>
        subscribers foreach (_ ! msg)
      case msg@Subscribe(_, ref, _) =>
        context watch ref
        subscribers += ref
        ref ! msg

      case Terminated(ref) =>
        subscribers -= ref
        if (subscribers.isEmpty) context stop self
    }
  }

}

class MQTTClient(broker: URI, config: MQTTClient.Configuration = MQTTClient.Configuration()) extends FSM[MQTTClient.ClientState, Int] with ActorLogging {

  import MQTTClient._

  private[this] val messageCallback = new MqttCallback {
    override def deliveryComplete(token: IMqttDeliveryToken): Unit = {
      log.debug(s"deliveryComplete(${token.getTopics.mkString(",")})")
      // TODO handle acknowledge correctly for the sender
      //      self ! new Ack(token.getTopics.toSet, token.getMessage)
    }

    override def messageArrived(topic: String, message: MqttMessage): Unit = {
      log.debug(s"messageArrived($topic,${new String(message.getPayload, "UTF-8")})")
      self ! new Message(topic, message)
    }

    override def connectionLost(cause: Throwable): Unit = {
      log.debug(s"connectionLost(${cause.getMessage})")
      self ! Disconnected(Some(cause))
    }
  }

  private[this] val connectionCallback = new IMqttActionListener {
    override def onFailure(asyncActionToken: IMqttToken, exception: Throwable): Unit = {
      log.error(s"client disconnected: ${exception.getMessage}")
      self ! Disconnected(Some(exception))
    }

    override def onSuccess(asyncActionToken: IMqttToken): Unit = {
      log.debug(s"client connected")
      self ! Connected
    }
  }

  private[this] val client = {
    val client = new MqttAsyncClient(broker.toString, config.clientId.getOrElse(s"aviotar-mqtt-${UUID.randomUUID.toString}"))
    client.setCallback(messageCallback)
    client
  }

  // this contains the publish and subscribe messages while we are not yet connected
  private[this] val pubSubStash = ListBuffer.empty[(Deadline, Any)]

  @inline private def encode(s: String) = URLEncoder.encode(s, "UTF-8")

  @inline private def decode(s: String) = URLDecoder.decode(s, "UTF-8")

  // start the Finite State Machine in disconnected state
  startWith(ClientDisconnected, 0)

  // handle client disconnected state
  when(ClientDisconnected) {
    case Event(Connect, retries) =>
      log.info(s"connect(${broker.toString}, retries=$retries)")
      val mqttConnectOptions = new MqttConnectOptions
      config.userName.foreach(mqttConnectOptions.setUserName)
      config.password.foreach(p => mqttConnectOptions.setPassword(p.toCharArray))
      config.cleanSession.foreach(mqttConnectOptions.setCleanSession)
      config.connectionTimeout.foreach(mqttConnectOptions.setConnectionTimeout)
      config.keepAliveInterval.foreach(mqttConnectOptions.setKeepAliveInterval)

      client.connect(mqttConnectOptions, null, connectionCallback)
      stay using (retries + 1)

    case Event(Connected, retries) =>
      log.info(s"connected(${broker.toString}, retries=$retries)")
      for ((deadline, x) <- pubSubStash if deadline.hasTimeLeft()) self ! x
      pubSubStash.clear()
      goto(ClientConnected) using 0

    case Event(e@(_: Publish | _: Subscribe), _) =>
      log.info(s"$e")
      if (pubSubStash.length > config.stashSize) {
        pubSubStash.remove(0, config.stashSize / 2)
      }
      pubSubStash += Tuple2(Deadline.now + config.stashTTL, e)
      stay()
  }

  // handle client connected state
  when(ClientConnected) {
    case Event(Publish(topic, payload, qos), _) =>
      log.debug(s"publish($topic, ${new String(payload, "UTF-8")}, $qos)")
      client.publish(topic, payload, qos, false)
      stay()

    case Event(msg@Subscribe(topic, actor, qos), _) =>
      log.debug(s"subscribe($topic, ${actor.path.toString}, $qos)")
      val matchingActors = context.children.filter(a => decode(a.path.name) == topic)

      if (matchingActors.nonEmpty) {
        matchingActors.foreach(_ ! msg)
      } else {
        val topicActor = context.actorOf(Props[TopicActor], name = encode(topic))
        context.watch(topicActor)
        topicActor ! msg
        client.subscribe(topic, qos)
      }
      stay()
  }

  whenUnhandled {
    case Event(msg: Message, _) =>
      log.debug(msg.toString)
      context.children.filter {
        a =>
          val matcher = decode(a.path.name).replaceAll("[#]", ".*").replaceAll("[+]", "[^/]+")
          msg.topic.matches(matcher)
      }.foreach(_ ! msg)
      stay()

    case Event(Terminated(topicActorRef), _) =>
      val name = decode(topicActorRef.path.name)
      log.debug(s"topic actor terminated: $name")
      if (name.matches("[+#]") && context.children.count(c => decode(c.path.name) == name && c != topicActorRef) != 0) {
        log.debug(s"can't unsubscribe from $name due to active recipients")
      } else {
        client.unsubscribe(topicActorRef.path.name)
      }
      stay()

    case Event(Disconnected(throwable), _) =>
      throwable.foreach(e => log.debug(s"disconnected(${e.getMessage}"))
      setTimer("reconnect", Connect, 10 seconds)
      goto(ClientDisconnected)
  }

  initialize()

  self ! Connect
}

