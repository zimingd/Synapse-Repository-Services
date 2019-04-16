package org.sagebionetworks.util;

public class TimedAssert {
	/**
	 * Wait a specified time for asserts to not fail (anymore). Assumes condition.run() will throw AssertionError for a
	 * while.
	 * 
	 * @param maxTimeMillis
	 * @param checkIntervalMillis
	 * @param condition
	 */
	public static void waitForAssert(long maxTimeMillis, long checkIntervalMillis, final Runnable condition) {
		if (!TimeUtils.waitFor(maxTimeMillis, checkIntervalMillis, null, input -> {
			try {
				condition.run();
				return true;
			} catch (AssertionError e) {
				return false;
			}
		})) {
			// run condition one final time tp have assertion error thrown out of this method
			condition.run();
		}
	}
}
