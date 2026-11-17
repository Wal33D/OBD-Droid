package com.obddroid.services;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.ParcelUuid;

import com.obddroid.obd.StreamHandler;
import com.obddroid.utils.PermissionManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

/**
 * This class does all the work for setting up and managing Bluetooth
 * connections with other devices. It has a thread that listens for incoming
 * connections, a thread for connecting with a device, and a thread for
 * performing data transmissions when connected.
 */
@SuppressLint("NewApi")
public class BluetoothCommService extends CommService
{

	private BtConnectThread mBtConnectThread;
	private BtWorkerThread mBtWorkerThread;
	/** communication stream handler */
	private final StreamHandler ser = new StreamHandler();
	/** Lock for thread-safe connection state management */
	private final ReentrantLock connectionLock = new ReentrantLock();
	/** Timeout for thread cleanup operations in milliseconds */
	private static final long THREAD_CLEANUP_TIMEOUT_MS = 2000;
	/** Post-connection stabilization delay in milliseconds */
	private static final long POST_CONNECTION_DELAY_MS = 300;
	
	
	/**
	 * Constructor. Prepares a new Bluetooth Communication session.
	 *
	 * @param context The UI Activity Context
	 * @param handler A Handler to send messages back to the UI Activity
	 */
	@SuppressLint("MissingPermission")
	public BluetoothCommService(Context context, Handler handler)
	{
		super(context, handler);

		// Check if we have Bluetooth permissions
		if (!PermissionManager.hasBluetoothPermissions(context)) {
			log.log(Level.WARNING, "Bluetooth permissions not granted");
			// Still continue setup but operations will fail
		}

		try {
			// Always cancel discovery because it will slow down a connection
			BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
			BluetoothAdapter mAdapter = bluetoothManager != null ? bluetoothManager.getAdapter() : null;
			if (mAdapter != null && PermissionManager.hasBluetoothPermissions(context)) {
				// Only cancel discovery if we have permission
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
					// Android 12+ requires BLUETOOTH_SCAN permission
					mAdapter.cancelDiscovery();
				} else {
					// Older versions can cancel without runtime permission
					mAdapter.cancelDiscovery();
				}
			}
		} catch (SecurityException e) {
			log.log(Level.WARNING, "Cannot cancel discovery - missing Bluetooth permission", e);
		}

		// set up protocol handlers
		elm.addTelegramWriter(ser);
		ser.setMessageHandler(elm);
	}

	/**
	 * Start the chat service. Specifically start AcceptThread to begin a session
	 * in listening (server) mode. Called by the Activity onResume()
	 */
	@Override
	public synchronized void start()
	{
		log.log(Level.FINE, "start");

		connectionLock.lock();
		try {
			// Cancel and wait for any thread attempting to make a connection
			cleanupConnectThread();

			// Cancel and wait for any thread currently running a connection
			cleanupWorkerThread();

			setState(STATE.LISTEN);
		} finally {
			connectionLock.unlock();
		}
	}

	/**
	 * start connection to specified device
	 *
	 * @param device The device to connect
	 * @param secure Socket Security type - Secure (true) , Insecure (false)
	 */
	@Override
	public synchronized void connect(Object device, boolean secure)
	{
		log.log(Level.FINE, "connect to: " + device);

		// Check state early to avoid unnecessary queueing
		if (getState() == STATE.CONNECTING || getState() == STATE.CONNECTED) {
			log.log(Level.WARNING, "Already connecting/connected - ignoring connect request");
			return;
		}

		// Use coordinator ASYNC to prevent ANR on UI thread
		CommService.coordinator.executeExclusive(
			"BluetoothCommService.connect",
			RequestPriority.CONNECTION,
			() -> {
				connectionLock.lock();
				try {
					// Double-check state inside lock
					if (getState() == STATE.NONE || getState() == STATE.OFFLINE || getState() == STATE.LISTEN) {
						// Clean up any existing connection threads before starting new connection
						cleanupConnectThread();
						cleanupWorkerThread();

						setState(STATE.CONNECTING);

						// Start the thread to connect with the given device
						mBtConnectThread = new BtConnectThread((BluetoothDevice)device, secure);
						mBtConnectThread.start();
					} else {
						log.log(Level.WARNING, "State changed to " + getState() + " - skipping connect");
					}
				} finally {
					connectionLock.unlock();
				}
				return null;
			}
		).exceptionally(e -> {
			log.log(Level.WARNING, "Connection failed via coordinator", e);
			setState(STATE.OFFLINE);
			return null;
		});
	}

	/**
	 * Start the BtWorkerThread to begin managing a Bluetooth connection
	 *
	 * @param socket The BluetoothSocket on which the connection was made
	 * @param device The BluetoothDevice that has been connected
	 */
	private synchronized void connected(BluetoothSocket socket, BluetoothDevice
		                                                            device, final String socketType)
	{
		log.log(Level.FINE, "connected, Socket Type:" + socketType);

		connectionLock.lock();
		try {
			// Cancel the thread that completed the connection
			cleanupConnectThread();

			// Cancel any thread currently running a connection
			cleanupWorkerThread();

			// Brief delay for connection stabilization (reduced from 500ms)
			if (POST_CONNECTION_DELAY_MS > 0) {
				try {
					Thread.sleep(POST_CONNECTION_DELAY_MS);
				} catch (InterruptedException e) {
					log.log(Level.WARNING, "Connection delay interrupted", e);
					Thread.currentThread().interrupt();
				}
			}

			// Start the thread to manage the connection and perform transmissions
			mBtWorkerThread = new BtWorkerThread(socket, socketType);
			mBtWorkerThread.start();

			// we are connected -> signal connection established
			connectionEstablished(device.getName());
		} finally {
			connectionLock.unlock();
		}
	}

	/**
	 * Stop all threads
	 */
	@Override
	public synchronized void stop()
	{
		log.log(Level.FINE, "stop");

		// Check state early to avoid unnecessary queueing
		if (getState() == STATE.OFFLINE) {
			log.log(Level.FINE, "Already offline - ignoring stop request");
			return;
		}

		// Use coordinator ASYNC to deduplicate multiple stop calls during disconnect storm
		// CRITICAL: Don't block UI thread!
		CommService.coordinator.executeExclusive(
			"BluetoothCommService.stop",
			RequestPriority.CONNECTION,
			() -> {
				connectionLock.lock();
				try {
					// Double-check state inside lock
					if (getState() != STATE.OFFLINE) {
						elm.removeTelegramWriter(ser);

						// Properly cleanup all threads with timeout
						cleanupConnectThread();
						cleanupWorkerThread();

						setState(STATE.OFFLINE);
					} else {
						log.log(Level.FINE, "Already offline inside lock - skipping cleanup");
					}
				} finally {
					connectionLock.unlock();
				}
				return null;
			}
		).exceptionally(e -> {
			log.log(Level.WARNING, "Stop failed via coordinator", e);
			// Force offline state even if coordinator fails
			setState(STATE.OFFLINE);
			return null;
		});
	}

	/**
	 * Cleanup connect thread with proper join and timeout
	 */
	private void cleanupConnectThread()
	{
		if (mBtConnectThread != null)
		{
			log.log(Level.FINE, "Cleaning up connect thread");
			mBtConnectThread.cancel();
			try {
				// Wait for thread to finish with timeout
				mBtConnectThread.join(THREAD_CLEANUP_TIMEOUT_MS);
				if (mBtConnectThread.isAlive()) {
					log.log(Level.WARNING, "Connect thread did not terminate within timeout");
					// Interrupt the thread as last resort
					mBtConnectThread.interrupt();
				}
			} catch (InterruptedException e) {
				log.log(Level.WARNING, "Interrupted while waiting for connect thread cleanup", e);
				Thread.currentThread().interrupt();
			}
			mBtConnectThread = null;
		}
	}

	/**
	 * Cleanup worker thread with proper join and timeout
	 */
	private void cleanupWorkerThread()
	{
		if (mBtWorkerThread != null)
		{
			log.log(Level.FINE, "Cleaning up worker thread");
			mBtWorkerThread.cancel();
			try {
				// Wait for thread to finish with timeout
				mBtWorkerThread.join(THREAD_CLEANUP_TIMEOUT_MS);
				if (mBtWorkerThread.isAlive()) {
					log.log(Level.WARNING, "Worker thread did not terminate within timeout");
					// Interrupt the thread as last resort
					mBtWorkerThread.interrupt();
				}
			} catch (InterruptedException e) {
				log.log(Level.WARNING, "Interrupted while waiting for worker thread cleanup", e);
				Thread.currentThread().interrupt();
			}
			mBtWorkerThread = null;
		}
	}

	/**
	 * Write to the BtWorkerThread in an un-synchronized manner
	 *
	 * @param out The bytes to write
	 * @see BtWorkerThread#write(byte[])
	 */
	@Override
	public synchronized void write(byte[] out)
	{
		// Perform the write un-synchronized
		mBtWorkerThread.write(out);
	}

	/**
	 * This thread runs while attempting to make an outgoing connection with a
	 * device. It runs straight through; the connection either succeeds or fails.
	 */
	private class BtConnectThread extends Thread
	{
		private final BluetoothDevice mmDevice;
		private BluetoothSocket mmSocket;
		private final String mSocketType;
		private volatile boolean running = true;

		@SuppressLint("MissingPermission")
		BtConnectThread(BluetoothDevice device, boolean secure)
		{
			mmDevice = device;
			BluetoothSocket tmp = null;
			mSocketType = secure ? "Secure" : "Insecure";

			// Modified to work with SPP Devices
			final UUID SPP_UUID = UUID
				.fromString("00001101-0000-1000-8000-00805F9B34FB");

			// Get a BluetoothSocket for a connection with the
			// given BluetoothDevice
			try
			{
				// Android 12+ requires BLUETOOTH_CONNECT permission
				if (secure)
				{
					tmp = device.createRfcommSocketToServiceRecord(SPP_UUID);
				} else
				{
					tmp = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID);
				}
			} catch (IOException e)
			{
				log.log(Level.SEVERE, "Socket Type: " + mSocketType + "create() failed", e);
			} catch (SecurityException e)
			{
				log.log(Level.SEVERE, "Missing BLUETOOTH_CONNECT permission", e);
			}
			mmSocket = tmp;

			logSocketUuids(mmSocket, "BT socket");
		}
		
		/**
		 * Log supported UUIDs of specified BluetoothSocket
		 * @param socket Socket to log
		 * @param msg Message to prepend the UUIDs
		 */
		@SuppressLint("MissingPermission")
		private void logSocketUuids(BluetoothSocket socket, String msg)
		{
			if(log.isLoggable(Level.INFO))
			{
				try {
					StringBuilder message = new StringBuilder(msg);
					// dump supported UUID's
					message.append(" - UUIDs:");
					// Android 12+ requires BLUETOOTH_CONNECT permission
					ParcelUuid[] uuids = socket.getRemoteDevice().getUuids();
					if(uuids != null)
					{
						for (ParcelUuid uuid: uuids)
						{
							message.append(uuid.getUuid().toString()).append(",");
						}
					}
					else
					{
						message.append("NONE (Invalid BT implementation)");
					}
					log.log(Level.INFO, message.toString());
				} catch (SecurityException e) {
					log.log(Level.WARNING, "Cannot get UUIDs - missing BLUETOOTH_CONNECT permission");
				}
			}
		}
		
		public void run()
		{
			log.log(Level.INFO, "BEGIN mBtConnectThread SocketType:" + mSocketType);

			// Check if we should still be running
			if (!running) {
				log.log(Level.INFO, "Connect thread cancelled before start");
				return;
			}

			// Make a connection to the BluetoothSocket
			try
			{
				log.log(Level.FINE, "Connect BT socket");

				// This is a blocking call and will only return on a
				// successful connection or an exception
				mmSocket.connect();
			}
			catch (IOException e)
			{
				// Check if cancelled during connection attempt
				if (!running) {
					log.log(Level.INFO, "Connect thread cancelled during connection");
					return;
				}

				log.log(Level.FINE, e.getMessage());
				cancel();

				log.log(Level.INFO, "Fallback attempt to create RfComm socket");
				BluetoothSocket sockFallback;
				Class<?> clazz = mmSocket.getRemoteDevice().getClass();
				Class<?>[] paramTypes = new Class<?>[]{Integer.TYPE};
				try {
					// Check again if we should continue
					if (!running) {
						log.log(Level.INFO, "Connect thread cancelled before fallback");
						return;
					}

					//noinspection JavaReflectionMemberAccess
					Method m = clazz.getMethod("createRfcommSocket", paramTypes);
					Object[] params = new Object[]{1};
					sockFallback = (BluetoothSocket) m.invoke(mmSocket.getRemoteDevice(), params);
					mmSocket = sockFallback;

					logSocketUuids(mmSocket, "Fallback socket");

					// connect fallback socket
					mmSocket.connect();
				}
				catch (IOException | ReflectiveOperationException | SecurityException e2)
				{
					log.log(Level.SEVERE, e2.getMessage());
					if (running) {
						connectionFailed();
					}
					return;
				}
			}

			// Final check before proceeding to connected state
			if (!running) {
				log.log(Level.INFO, "Connect thread cancelled after connection");
				return;
			}

			// Reset the BtConnectThread because we're done
			synchronized (BluetoothCommService.this)
			{
				mBtConnectThread = null;
			}

			// Start the connected thread
			connected(mmSocket, mmDevice, mSocketType);
		}

		synchronized void cancel()
		{
			running = false;
			try
			{
				if (mmSocket != null) {
					log.log(Level.INFO, "Closing BT connect socket");
					mmSocket.close();
				}
			} catch (IOException e)
			{
				log.log(Level.WARNING, "Error closing connect socket: " + e.getMessage());
			}
		}
	}

	/**
	 * This thread runs during a connection with a remote device. It handles all
	 * incoming and outgoing transmissions.
	 */
	private class BtWorkerThread extends Thread
	{
		private final BluetoothSocket mmSocket;
		private final InputStream mmInStream;
		private final OutputStream mmOutStream;
		private volatile boolean running = true;

		BtWorkerThread(BluetoothSocket socket, String socketType)
		{
			log.log(Level.FINE, "create BtWorkerThread: " + socketType);
			mmSocket = socket;
			InputStream tmpIn = null;
			OutputStream tmpOut = null;

			// Get the BluetoothSocket input and output streams
			try
			{
				tmpIn = socket.getInputStream();
				tmpOut = socket.getOutputStream();
			} catch (IOException e)
			{
				log.log(Level.SEVERE, "temp sockets not created", e);
			}

			mmInStream = tmpIn;
			mmOutStream = tmpOut;
			// set streams
			ser.setStreams(mmInStream, mmOutStream);
		}

		/**
		 * run the main communication loop
		 */
		public void run()
		{
			log.log(Level.INFO, "BEGIN mBtWorkerThread");

			// Check if we should still be running
			if (!running) {
				log.log(Level.INFO, "Worker thread cancelled before start");
				return;
			}

			try
			{
				// run the communication thread
				ser.run();
			} catch (Exception ex)
			{
				// Only log if we're still supposed to be running (not cancelled)
				if (running) {
					log.log(Level.SEVERE, "Comm thread aborted", ex);
				} else {
					log.log(Level.INFO, "Comm thread stopped (cancelled)");
				}
			}

			// Only report connection lost if we were still supposed to be running
			if (running) {
				connectionLost();
			}
		}

		/**
		 * Write to the connected OutStream.
		 *
		 * @param buffer The bytes to write
		 */
		synchronized void write(byte[] buffer)
		{
			if (running) {
				ser.writeTelegram(new String(buffer).toCharArray());
			} else {
				log.log(Level.WARNING, "Attempted write to cancelled worker thread");
			}
		}

		synchronized void cancel()
		{
			running = false;
			try
			{
				if (mmSocket != null) {
					log.log(Level.INFO, "Closing BT worker socket");
					mmSocket.close();
				}
			} catch (IOException e)
			{
				log.log(Level.WARNING, "Error closing worker socket: " + e.getMessage());
			}
		}

	}
}
