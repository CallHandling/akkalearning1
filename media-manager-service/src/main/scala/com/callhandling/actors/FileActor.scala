package com.callhandling.actors

import akka.actor.{Actor, Props}

object FileActor {
  def props(id: String): Props = Props(FileActor(id))

  final case class SetFilename(filename: String)
  final case class SetDescription(description: String)
  case object InfoUpdated
  case object Display
}

case class FileActor(id: String) extends Actor {
  import FileActor._

  def update(filename: String, description: String): Unit =
    context.become(receive(filename, description), discardOld = true)

  def receive(filename: String, description: String): Receive = {
    case SetFilename(newFilename) =>
      update(newFilename, description)
      sender() ! InfoUpdated
    case SetDescription(newDescription) => update(filename, newDescription)
    case Display =>
      println(s"ID: $id, Filename: $filename, Description: $description")
  }

  override def receive = receive("", "")
}
