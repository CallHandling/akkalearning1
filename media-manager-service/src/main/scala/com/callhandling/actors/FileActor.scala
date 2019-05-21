package com.callhandling.actors

import akka.actor.{Actor, Props}

object FileActor {
  def props: Props = Props[FileActor]

  final case class SetId(uuid: String)
  final case class SetFilename(filename: String)
  final case class SetDescription(description: String)
  case object Display
}

class FileActor extends Actor {
  import FileActor._

  def update(id: String, filename: String, description: String): Unit =
    context.become(receive(id, filename, description), discardOld = true)

  def receive(id: String, filename: String, description: String): Receive = {
    case SetId(newId) => update(newId, filename, description)
    case SetFilename(newFilename) => update(id, newFilename, description)
    case SetDescription(newDescription) => update(id, filename, newDescription)
    case Display => println(s"Filename: $filename, Description: $description")
  }

  override def receive = receive("", "", "")
}
