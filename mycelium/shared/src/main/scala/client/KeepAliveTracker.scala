package mycelium.client

import java.util.{Timer, TimerTask}

import scala.concurrent.duration.FiniteDuration

private[client] class KeepAliveTracker(pingInterval: FiniteDuration, sendPing: () => Unit) {
  private val timer = new Timer
  private var currentTask = Option.empty[TimerTask]
  def acknowledgeTraffic(): Unit = {
    currentTask.foreach(_.cancel())
    timer.purge()
    val task = new TimerTask { def run() = sendPing() }
    timer.schedule(task, pingInterval.toMillis)
    currentTask = Some(task)
  }
}
