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

import akka.actor.Actor.Receive
import akka.actor._
import ubirch.aviotar.SensorRegistryBehavior.{Register, Unregister}

/**
 * Add description.
 *
 * @author Matthias L. Jugel
 */

object SensorRegistryBehavior {

  case class Register(id: String, sensor: Sensor)

  case class Unregister(id: String)

}

trait SensorRegistryBehavior {
  this: Actor with ActorLogging =>

  def sensorRegistration: Receive = {
    case Register(id, sensor) =>
      val sensorRef: ActorRef = context.actorOf(SensorActor(sensor), name = id)
      context.watch(sensorRef)
      log.debug(s"registered sensor $id at ${sensorRef.path.toString}")
    case Unregister(id) =>
      val sensorRef = context.actorSelection(id)
      sensorRef ! PoisonPill
      log.debug(s"unregistered sensor $id at ${sensorRef.pathString}")
  }
}

// ================================================================================
object SensorActor {
  def apply(sensor: Sensor): Props = Props(new SensorActor(sensor))
}

class SensorActor(sensor: Sensor) extends Actor with ActorLogging {
  override def receive: Receive = sensor.mark
}

trait Sensor {
  def mark: Receive
}