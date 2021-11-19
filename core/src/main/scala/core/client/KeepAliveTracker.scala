package mycelium.core.client

import scala.concurrent.duration.FiniteDuration
import java.util.{Timer, TimerTask}

private[mycelium] class KeepAliveTracker(
    pingInterval: FiniteDuration,
    sendPing: () => Unit,
) {
  private val timer       = new Timer
  private var currentTask = Option.empty[TimerTask]
  def acknowledgeTraffic(): Unit = {
    currentTask.foreach(_.cancel())
    timer.purge()
    val task = new TimerTask { def run() = sendPing() }
    timer.schedule(task, pingInterval.toMillis)
    currentTask = Some(task)
  }
}
