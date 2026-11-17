package com.obddroid.utils;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;

import com.obddroid.ecu.EcuDataPv;
import com.obddroid.obd.ElmProt;
import com.obddroid.obd.ObdProt;
import com.obddroid.common.ProcessVariables.ProcessVar;
import com.obddroid.common.ProcessVariables.PvChangeEvent;
import com.obddroid.common.ProcessVariables.PvList;
import com.obddroid.common.ProcessVariables.TypedPvList;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.text.SimpleDateFormat;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.obddroid.ui.activities.MainActivity;
import com.obddroid.services.CommService;
import com.obddroid.ui.adapters.ObdItemAdapter;
import com.obddroid.R;
import java.io.IOException;

/**
 * Task to save measurements

 */
public class FileHelper
{
	/** Date Formatter used to generate file name */
	@SuppressLint("SimpleDateFormat")
	private static final SimpleDateFormat dateFmt = new SimpleDateFormat("yyyy.MM.dd-HH.mm.ss");
	// Suppress deprecation - ProgressDialog still functional for simple progress indication
	@SuppressWarnings("deprecation")
	private static android.app.ProgressDialog progress;

	private static final Logger log = Logger.getLogger(FileHelper.class.getName());
	
	private final Context context;
	private final ElmProt elm;

	/**
	 * Initialize static data for static calls
	 *  @param context APP context
	 *
	 */
	public FileHelper(Context context)
	{
		this.context = context;
		this.elm = CommService.elm;
	}

	/**
	 * get default path for load/store operation
	 * * path is based on configured <user data location>/<package name>
	 *
	 * @return default path for current app context
	 */
	public static String getPath(Context context)
	{
		// Use app-specific external directory for Android 10+ (no permissions needed)
		File documentsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
		if (documentsDir != null)
		{
			return documentsDir.getAbsolutePath();
		}
		else
		{
			// Fallback to internal storage if external not available
			File internalDir = new File(context.getFilesDir(), "documents");
			internalDir.mkdirs();
			return internalDir.getAbsolutePath();
		}
	}

	/**
	 * get filename (w/o extension) based on current date & time
	 *
	 * @return file name
	 */
	static String getFileName()
	{
		return dateFmt.format(System.currentTimeMillis());
	}


	/**
	 * Save all data in a independent thread
	 */
	void saveDataThreaded()
	{
		// generate file name
		final String mPath = getPath(context);
		final String mFileName = mPath
			+ File.separator
			+ getFileName()
			+ ".obd";

		// create progress dialog
		// Suppress deprecation - ProgressDialog.show() still functional for simple progress indication
		@SuppressWarnings("deprecation")
		android.app.ProgressDialog progressDialog = android.app.ProgressDialog.show(context,
			context.getString(R.string.saving_data),
			mFileName,
			true);
		progress = progressDialog;

		Thread saveTask = new Thread()
		{
			public void run()
			{
				Looper.prepare();
				saveData(mPath, mFileName);
				progress.dismiss();
				Looper.loop();
			}
		};
		saveTask.start();
	}

	/**
	 * Save all data
	 */
	// Suppress deprecation - Using deprecated ObdProt fields for backward-compatible serialization
	@SuppressWarnings({"ResultOfMethodCallIgnored", "deprecation"})
	private synchronized void saveData(String mPath, String mFileName)
	{
		File outFile;

		// ensure the path is created
		//noinspection ResultOfMethodCallIgnored
		new File(mPath).mkdirs();
		outFile = new File(mFileName);

		// prevent data updates for saving period
		ObdItemAdapter.allowDataUpdates = false;

		try
		{
			outFile.createNewFile();
			FileOutputStream fStr = new FileOutputStream(outFile);
			ObjectOutputStream oStr = new ObjectOutputStream(fStr);
			oStr.writeInt(elm.getService());
			oStr.writeObject(ObdProt.PidPvs);
			oStr.writeObject(ObdProt.VidPvs);
			oStr.writeObject(ObdProt.tCodes);

			oStr.close();
			fStr.close();

			@SuppressLint("DefaultLocale")
			String msg = String.format("%s %d Bytes to %s",
				context.getString(R.string.saved),
				outFile.length(),
				mPath);
			log.info(msg);
			SnackbarHelper.showSuccess(context, msg, SnackbarHelper.Duration.SHORT);
		} catch (IOException e)
		{
			SnackbarHelper.showError(context, e.toString(), SnackbarHelper.Duration.SHORT);
			log.log(Level.WARNING, "Saving data failed", e);
		}

		// we are done saving, allow data updates again
		ObdItemAdapter.allowDataUpdates = true;
	}

	/**
	 * Load all data in a independent thread
	 * @param uri Uri of ile to be loaded
	 */
	public synchronized void loadDataThreaded(final Uri uri,
	                                   final Handler reportTo)
	{
		// create progress dialog
		// Suppress deprecation - ProgressDialog.show() still functional for simple progress indication
		@SuppressWarnings("deprecation")
		android.app.ProgressDialog progressDialog = android.app.ProgressDialog.show(context,
		                               context.getString(R.string.loading_data),
		                               uri.getPath(),
		                               true);
		progress = progressDialog;

		Thread loadTask = new Thread()
		{
			public void run()
			{
				Looper.prepare();
				loadData(uri);
				progress.dismiss();
				reportTo.sendMessage(reportTo.obtainMessage(MainActivity.MESSAGE_FILE_READ));
				Looper.loop();
			}
		};
		loadTask.start();
	}

	/**
	 * Load data from file into data structures
	 *
	 * @param uri URI of file to be loaded
	 */
	// Suppress deprecation - Using deprecated ObdProt fields for backward-compatible deserialization
    @SuppressLint("DefaultLocale")
    @SuppressWarnings({"UnusedReturnValue", "deprecation", "unchecked"})
    private synchronized int loadData(final Uri uri)
	{
		int numBytesLoaded = 0;
		String msg;
		InputStream inStr;

		try
		{
			inStr = context.getContentResolver().openInputStream(uri);
			numBytesLoaded = inStr != null ? inStr.available() : 0;
			msg = context.getString(R.string.loaded).concat(String.format(" %d Bytes", numBytesLoaded));
			ObjectInputStream oIn = new ObjectInputStream(inStr);
			/* ensure that measurement page is activated
			   to avoid deletion of loaded data afterwards */
			int currService = oIn.readInt();
			/* if data was saved in mode 0, keep current mode */
			if(currService != 0) elm.setService(currService, false);
			/* read in the data */
            Object pidObj = oIn.readObject();
            mergePvList(ObdProt.PidPvs, pidObj);
            Object vidObj = oIn.readObject();
            mergePvList(ObdProt.VidPvs, vidObj);
            Object codeObj = oIn.readObject();
            mergePvList(ObdProt.tCodes, codeObj);
			// Plugin data removed - try to skip if present in old files
			try {
				oIn.readObject(); // Skip plugin data if present
			} catch (IOException | ClassNotFoundException ignored) {
				// Ignore if no plugin data in file
			}

			oIn.close();

			log.log(Level.INFO, msg);
			SnackbarHelper.showSuccess(context, msg, SnackbarHelper.Duration.SHORT);
		} catch (IOException | ClassNotFoundException | ClassCastException | SecurityException ex)
		{
			SnackbarHelper.showError(context, ex.toString(), SnackbarHelper.Duration.SHORT);
			log.log(Level.SEVERE, uri.toString(), ex);
		}
		return numBytesLoaded;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private void mergePvList(PvList target, Object source) {
		if (target == null || source == null) {
			return;
		}

		if (source instanceof PvList) {
			target.clear();
			target.putAll((PvList) source, PvChangeEvent.PV_ADDED, false);
		} else if (source instanceof Map) {
			target.clear();
			target.putAll((Map) source);
		}
	}
}
