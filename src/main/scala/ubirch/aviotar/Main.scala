package ubirch.aviotar

import java.util.UUID

import net.liftweb.json.JsonDSL._
import net.liftweb.json.Serialization.write
import net.liftweb.json._
import org.eclipse.paho.client.mqttv3._

import scala.util.Random

/**
 * Add description.
 *
 * @author Matthias L. Jugel
 */
object Main extends App {

  implicit val execution = scala.concurrent.ExecutionContext.Implicits.global
  implicit val formats = net.liftweb.json.DefaultFormats

  val subscriber = new MqttClient("tcp://localhost:1883", "storage-client")

  subscriber.setCallback(new MqttCallback {
    override def deliveryComplete(iMqttDeliveryToken: IMqttDeliveryToken): Unit = {
      println("TOKEN: " + iMqttDeliveryToken.getMessage)
    }

    override def messageArrived(s: String, msg: MqttMessage): Unit = try {
      val index = s.split('/').filter(_ != "").mkString("-")
      val data = parse(new String(msg.getPayload, "UTF-8"))
      ElasticSearch.index(index, "data", data).map(r => println(" -> " + write(r)))
        .onFailure {
        case e: Exception => e.printStackTrace()
      }
    } catch {
      case e: Exception =>
        e.printStackTrace()
    }

    override def connectionLost(throwable: Throwable): Unit = {
      throw throwable
    }

  })

  subscriber.connect()
  subscriber.subscribe("/sensors/#")

  val publisher = new MqttClient("tcp://localhost:1883", "storage-client-publisher")
  publisher.connectWithResult(new MqttConnectOptions)
  while (true) {
    val payload = ("id" -> UUID.randomUUID().toString) ~ ("value" -> Random.nextInt(10))
    val jsonPayload = write(payload)
    publisher.publish(s"/sensors/sensor-${Random.nextInt(10)}", jsonPayload.getBytes("UTF-8"), 0, true)
  }

  //  publisher.disconnect()
  //  subscriber.disconnect()

}
