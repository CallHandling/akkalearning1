package com.callhandling.actors

import akka.actor.Actor

trait ImmutableActor[S] extends Actor {
  val initialState: S
  def receive(state: S): Receive
  def update(state: S): Unit =
    context.become(receive(state), discardOld = true)
  override def receive: Receive = receive(initialState)
}
