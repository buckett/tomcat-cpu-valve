package org.bumph;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.ServletException;

import org.apache.catalina.CometEvent;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.Valve;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.util.LifecycleSupport;
import org.apache.catalina.valves.ValveBase;

/**
 * This Valve limits the amount of CPU time a request can take.
 * 
 * @author buckett
 */
public class CPUValve extends ValveBase implements Lifecycle {

	private Valve next;
	private LifecycleSupport lifecycleSuport = new LifecycleSupport(this);
	private Thread watcherThread;
	
	protected ThreadMXBean mxbean;
	protected boolean supported;
	
	// These are all the threads that are currently executing.
	protected Map<Thread, Long> executing = new ConcurrentHashMap<Thread, Long>();
	
	/**
	 * Maximum time in seconds a request can use of CPU.
	 */
	protected long max = 10000;

	/**
	 * Accuracy as a percentage that the maximum time needs to be enforce to.
	 */
	protected float accuracy = 0.1f;

	public void setMax(long max) {
		this.max = max;
	}

	public void setAccuracy(float accuracy) {
		this.accuracy = accuracy;
	}

	public CPUValve() {
		mxbean = ManagementFactory.getThreadMXBean();
		supported = mxbean.isThreadCpuTimeSupported();
	}

	public void backgroundProcess() {
		// Don't run the thread if it's not supported.
		if (!supported) {
			return;
		}
		Runnable watcher = new Runnable() {

			@SuppressWarnings("deprecation")
			public void run() {
				while (true) {
					for (Map.Entry<Thread, Long> running : executing.entrySet()) {
						Thread thread = running.getKey();
						if (isTimeUp(thread, running.getValue())) {
							// First pause the thread so we can be sure it is
							// still the same
							thread.suspend();

							// Double check
							Long cpu = executing.get(thread);
							if (cpu != null && isTimeUp(thread, cpu)) {
								// This might be deprecated but it's not much
								// worse then
								// a StackOverflowException
								running.getKey().stop();
							}
						}
					}
					try {
						// Check every so often.
						Thread.sleep(Math.round(max * accuracy));
					} catch (InterruptedException e) {
						// Ignore
					}
				}
			}

			// Check if the thread should be killed.
			private boolean isTimeUp(Thread thread, long initial) {
				long currently = mxbean.getThreadCpuTime(thread.getId());
				return (currently - initial) > max * 1000000; // milli to nano
			}
		};
		watcherThread = new Thread(watcher);
		watcherThread.setDaemon(true);
		watcherThread.setName("CPU Valve Watcher");
		watcherThread.start();
	}

	public void event(Request arg0, Response arg1, CometEvent arg2)
			throws IOException, ServletException {
		next.event(arg0, arg1, arg2);
	}

	public String getInfo() {
		return "Tracks how much CPU each request takes and kills excesive use	.";
	}

	public Valve getNext() {
		return next;
	}

	public void invoke(Request request, Response response) throws IOException,
			ServletException {
		long started = 0;
		if (supported) {
			if (!mxbean.isThreadCpuTimeEnabled()) {
				mxbean.setThreadCpuTimeEnabled(true);
			}
			started = mxbean.getCurrentThreadCpuTime();
			executing.put(Thread.currentThread(), started);
		}

		try {
			next.invoke(request, response);
		} finally {
			if (supported) {
				executing.remove(Thread.currentThread());
			}
		}

	}

	public void setNext(Valve next) {
		this.next = next;
	}

	public void addLifecycleListener(LifecycleListener arg0) {
		lifecycleSuport.addLifecycleListener(arg0);
	}

	public LifecycleListener[] findLifecycleListeners() {
		return lifecycleSuport.findLifecycleListeners();
	}

	public void removeLifecycleListener(LifecycleListener arg0) {
		lifecycleSuport.removeLifecycleListener(arg0);
	}

	public void start() throws LifecycleException {
		lifecycleSuport.fireLifecycleEvent(Lifecycle.START_EVENT, null);
	}

	public void stop() throws LifecycleException {
		lifecycleSuport.fireLifecycleEvent(Lifecycle.START_EVENT, null);
	}

}
