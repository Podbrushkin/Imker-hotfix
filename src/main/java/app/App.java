package app;

import java.io.IOException;

import javax.security.auth.login.LoginException;

interface WikiAPI {

	/**
	 * Fetch something from the WikiAPI
	 * 
	 * @return the result from the API
	 * @throws IOException
	 *             if a network issue occurs
	 * @throws LoginException
	 */
	Object fetch() throws IOException, LoginException;
}

public class App {
	private static int exceptionCount = 0;
	protected static final int MAX_FAILS = 3;
	protected static final int MAX_EXCEPTION_SLEEP_TIME = 30 * 1000; // ms

	/**
	 * Attempt to fetch from the given api a maximum of maxFails and wait some
	 * time (at most maxExceptionSleepTime) between tries.
	 * 
	 * @param api
	 *            the API to fetch from
	 * @param maxFails
	 *            the maximum number of exceptions to tolerate
	 * @param maxExceptionSleepTime
	 *            the maximum time to sleep in ms when an exception occurs;
	 *            scaled by maxFails^-2
	 * @return the result from the API
	 * @throws IOException
	 *             when a network error occurs
	 * @throws LoginException
	 */
	protected static Object attemptFetch(WikiAPI api, int maxFails,
			int maxExceptionSleepTime) throws IOException, LoginException {
		maxFails--;
		try {
			return api.fetch();
		} catch (IOException e) {
			exceptionCount++;
			if (maxFails == 0) {
				throw e;
			} else {
				try {
					Thread.sleep(maxExceptionSleepTime / (maxFails * maxFails));
				} catch (InterruptedException ignore) {
				}
				return attemptFetch(api, maxFails, maxExceptionSleepTime);
			}
		}
	}

	public static int getExceptionCount() {
		return exceptionCount;
	}
}
