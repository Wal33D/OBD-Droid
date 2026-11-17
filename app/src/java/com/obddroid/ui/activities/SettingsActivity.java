

package com.obddroid.ui.activities;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.obddroid.ecu.EcuDataItem;
import com.obddroid.obd.ElmProt;
import com.obddroid.obd.ObdProt;
import com.obddroid.services.telemetry.RemoteTelemetryManager;
import com.obddroid.services.CommService;
import com.obddroid.R;
import com.obddroid.utils.SecurePreferences;
import com.obddroid.utils.SnackbarHelper;

import java.text.DateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SettingsActivity
	extends AppCompatActivity
{
	/** The logger object */
	private static final Logger log = Logger.getLogger(SettingsActivity.class.getName());
	
	/**
	 * app preferences
	 */
	private static SharedPreferences prefs;
	/**
	 * key ids for device network settings
	 */
	private static final String[] networkKeys =
	{
		"device_address",
		"device_port"
	};
	/**
	 * key ids for device network settings
	 */
	private static final String[] bluetoothKeys =
	{
		"bt_secure_connection"
	};
	/**
	 * key ids for device network settings
	 */
	private static final String[] usbKeys =
	{
			"comm_baudrate"
	};

	// Preference key for data items
	public static final String KEY_DATA_ITEMS = "data_items";
	static final String KEY_PROT_SELECT = "protocol";
	static final String KEY_COMM_MEDIUM = "comm_medium";
	static final String ELM_MIN_TIMEOUT = "elm_min_timeout";
	static final String ELM_CMD_DISABLE = "elm_cmd_disable";
    static final String ELM_TIMING_SELECT = "adaptive_timing_mode";

	/*
	 * (non-Javadoc)
	 *
	 * @see android.preference.PreferenceActivity#onCreate(android.os.Bundle)
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setTheme(R.style.AppTheme_NoActionBar);
		prefs = PreferenceManager.getDefaultSharedPreferences(this);

		// Set the custom layout first so we can access the toolbar
		setContentView(R.layout.activity_settings);

		// Set up custom toolbar
		Toolbar toolbar = findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);

		// Apply full screen based on preference
		if(prefs.getBoolean(MainActivity.PREF_FULLSCREEN, false))
		{
			// Ultra-dark mode using modern WindowInsetsController
			WindowInsetsControllerCompat windowInsetsController = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
			if (windowInsetsController != null) {
				windowInsetsController.hide(WindowInsetsCompat.Type.systemBars());
				windowInsetsController.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
			}
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
				getWindow().setNavigationBarColor(androidx.core.content.ContextCompat.getColor(this, R.color.background_secondary));
			}
			// Make the toolbar black
			if (toolbar != null) {
				toolbar.setBackgroundColor(Color.BLACK);
			}
		}
		else
		{
			// Set status bar and navigation bar colors to match the toolbar
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
				getWindow().setStatusBarColor(Color.parseColor("#212121"));
				getWindow().setNavigationBarColor(androidx.core.content.ContextCompat.getColor(this, R.color.background_secondary));
			}
			// Ensure toolbar is dark grey
			if (toolbar != null) {
				toolbar.setBackgroundColor(Color.parseColor("#212121"));
			}
		}

		// Configure ActionBar - hide default title since we have custom TextView
		ActionBar actionBar = getSupportActionBar();
		if (actionBar != null)
		{
			actionBar.setDisplayShowTitleEnabled(false);
			actionBar.setDisplayHomeAsUpEnabled(false);
		}

		// Set up the back button on the right
		ImageButton backButton = findViewById(R.id.back_button);
		backButton.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				finish();
			}
		});

		// Display the fragment in the container
		getSupportFragmentManager().beginTransaction().replace(R.id.settings_container,
		                                                new PrefsFragment()).commit();
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		if (item.getItemId() == android.R.id.home)
		{
			finish();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	public static class PrefsFragment
		extends PreferenceFragmentCompat
		implements SharedPreferences.OnSharedPreferenceChangeListener
	{
		Vector<EcuDataItem> items;
		private SecurePreferences securePreferences;

		@Override
		public void onCreatePreferences(Bundle savedInstanceState, String rootKey)
		{
			// Load the preferences from an XML resource
			setPreferencesFromResource(R.xml.settings, rootKey);
		}

		@Override
		public void onCreate(Bundle savedInstanceState)
		{
			super.onCreate(savedInstanceState);

			// Initialize secure preferences
			securePreferences = new SecurePreferences(requireContext());

			// Communication media, protocol, baudrate, IP/port moved to adapter selection screen
			// setupCommMediaSelection(); // REMOVED - now in UnifiedAdapterSelectionActivity
			// setupProtoSelection(); // REMOVED - now in UnifiedAdapterSelectionActivity
			// set up ELM command selection
			setupElmCmdSelection();
            // set up ELM adaptive timing mode selection
			setupElmTimingSelection();
            // set up selectable PID list
            setupPidSelection();
            setupRemoteTelemetryPreferences();
            // set up AI features
            setupAiFeatures();
            // set up CoPilot features
            setupCoPilotFeatures();
			// update network selection fields - REMOVED
			// updateNetworkSelections(); // REMOVED - now in UnifiedAdapterSelectionActivity
			// add handler for selection update
			prefs.registerOnSharedPreferenceChangeListener(this);
		}

		/**
		 * set up protocol selection
		 */
		void setupProtoSelection()
		{
			ListPreference pref = (ListPreference) findPreference(KEY_PROT_SELECT);
			ElmProt.PROT[] values = ElmProt.PROT.values();
			CharSequence[] titles = new CharSequence[values.length];
			CharSequence[] keys = new CharSequence[values.length];
			int i = 0;
			for (ElmProt.PROT proto : values)
			{
				titles[i] = proto.toString();
				keys[i] = String.valueOf(proto.ordinal());
				i++;
			}
			// set enries and keys
			pref.setEntries(titles);
			pref.setEntryValues(keys);
			pref.setDefaultValue(titles[0]);
			// show current selection
			pref.setSummary(pref.getEntry());
		}

        /**
         * set up protocol selection
         */
        void setupElmTimingSelection()
        {
            ListPreference pref = (ListPreference) findPreference(ELM_TIMING_SELECT);
            ElmProt.AdaptTimingMode[] values = ElmProt.AdaptTimingMode.values();
            CharSequence[] titles = new CharSequence[values.length];
            CharSequence[] keys = new CharSequence[values.length];
            int i = 0;
            for (ElmProt.AdaptTimingMode mode : values)
            {
				titles[i] = mode.toString();
				keys[i] = mode.toString();
				i++;
            }
            // set entries and keys
            pref.setEntries(titles);
            pref.setEntryValues(keys);
            pref.setDefaultValue(titles[0]);
            // show current selection
            pref.setSummary(pref.getEntry());
        }

		/**
		 * set up protocol selection
		 */
		void setupElmCmdSelection()
		{
			MultiSelectListPreference pref =
				(MultiSelectListPreference) findPreference(ELM_CMD_DISABLE);
			ElmProt.CMD[] values = ElmProt.CMD.values();
			HashSet<String> selections = new HashSet<>();
			CharSequence[] titles = new CharSequence[values.length];
			CharSequence[] keys = new CharSequence[values.length];
			int i = 0;
			for (ElmProt.CMD cmd : values)
			{
				titles[i] = cmd.toString();
				keys[i] = cmd.toString();
				if(!cmd.isEnabled()) selections.add(cmd.toString());
				i++;
			}
			// set enries and keys
			pref.setEntries(titles);
			pref.setEntryValues(keys);
			pref.setValues(selections);
		}

		/**
		 * set up protocol selection
		 */
		void setupCommMediaSelection()
		{
			ListPreference pref = (ListPreference) findPreference(KEY_COMM_MEDIUM);
			CommService.MEDIUM[] values = CommService.MEDIUM.values();
			CharSequence[] titles = new CharSequence[values.length];
			CharSequence[] keys = new CharSequence[values.length];
			int i = 0;
			for (CommService.MEDIUM proto : values)
			{
				titles[i] = proto.toString();
				keys[i] = String.valueOf(proto.ordinal());
				i++;
			}
			// set enries and keys
			pref.setEntries(titles);
			pref.setEntryValues(keys);
			pref.setDefaultValue(titles[0]);
			// show current selection
			pref.setSummary(pref.getEntry());
		}

		/**
		 * set up selection for PIDs
		 */
		void setupPidSelection()
		{
			MultiSelectListPreference itemList =
				(MultiSelectListPreference) findPreference(KEY_DATA_ITEMS);

			// collect data items for selection
			items = ObdProt.dataItems.getSvcDataItems(ObdProt.OBD_SVC_DATA);
			HashSet<String> selections = new HashSet<>();
			CharSequence[] titles = new CharSequence[items.size()];
			CharSequence[] keys = new CharSequence[items.size()];
			// loop through data items
			int i = 0;
			for (EcuDataItem currItem : items)
			{
				titles[i] = currItem.label;
				keys[i] = currItem.toString();
				selections.add(currItem.toString());
				i++;
			}
			// set enries and keys
			itemList.setEntries(titles);
			itemList.setEntryValues(keys);

			// if there is no item selected, mark all as selected
			if (itemList.getValues().size() == 0)
			{
				itemList.setValues(selections);
			}
		}

		void setupRemoteTelemetryPreferences()
		{
			MultiSelectListPreference sharingItems =
				(MultiSelectListPreference) findPreference(RemoteTelemetryManager.PREF_SELECTED_ITEMS);
			if (sharingItems != null)
			{
				if (items == null || items.isEmpty())
				{
					items = ObdProt.dataItems.getSvcDataItems(ObdProt.OBD_SVC_DATA);
				}
				CharSequence[] titles = new CharSequence[items.size()];
				CharSequence[] keys = new CharSequence[items.size()];
				HashSet<String> defaults = new HashSet<>();
				int i = 0;
				for (EcuDataItem currItem : items)
				{
					titles[i] = currItem.label;
					keys[i] = currItem.toString();
					defaults.add(currItem.toString());
					i++;
				}
				sharingItems.setEntries(titles);
				sharingItems.setEntryValues(keys);
				if (sharingItems.getValues() == null || sharingItems.getValues().isEmpty())
				{
					sharingItems.setValues(defaults);
				}
				sharingItems.setSummaryProvider(preference -> {
					Set<String> values = ((MultiSelectListPreference) preference).getValues();
					if (values == null || values.isEmpty())
					{
						return getString(R.string.remote_telemetry_publish_all_items);
					}
					return getString(R.string.remote_telemetry_items_selected, values.size());
				});
			}

			EditTextPreference passwordPref = (EditTextPreference) findPreference(RemoteTelemetryManager.PREF_PASSWORD);
			if (passwordPref != null)
			{
				String existing = securePreferences.getRemoteTelemetryPassword();
				if (existing != null && !existing.isEmpty())
				{
					passwordPref.setSummary(R.string.remote_telemetry_password_configured);
				}
				else
				{
					passwordPref.setSummary(R.string.remote_telemetry_password_not_configured);
				}

				passwordPref.setOnPreferenceChangeListener((preference, newValue) ->
				{
					String password = String.valueOf(newValue);
					if (password.trim().isEmpty())
					{
						securePreferences.clearRemoteTelemetryPassword();
						passwordPref.setSummary(R.string.remote_telemetry_password_not_configured);
						SnackbarHelper.showInfo(getActivity(), getString(R.string.remote_telemetry_password_cleared));
					}
					else
					{
						securePreferences.setRemoteTelemetryPassword(password);
						passwordPref.setSummary(R.string.remote_telemetry_password_configured);
						SnackbarHelper.showSuccess(getActivity(), getString(R.string.remote_telemetry_password_saved));
					}
					passwordPref.setText("");
					return false;
				});
			}

			updateRemoteTelemetryStatusPreference();
		}

		private void updateRemoteTelemetryStatusPreference()
		{
			Preference statusPref = findPreference(RemoteTelemetryManager.PREF_STATUS);
			if (statusPref == null)
			{
				return;
			}

			// Check if fragment is attached before accessing context
			if (!isAdded() || getContext() == null) {
				return;
			}
			SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext());
			String statusCode = sharedPreferences.getString(RemoteTelemetryManager.PREF_LAST_STATUS_CODE, "");
			long timestamp = sharedPreferences.getLong(RemoteTelemetryManager.PREF_LAST_STATUS_TIME, 0L);
			String detail = sharedPreferences.getString(RemoteTelemetryManager.PREF_LAST_STATUS_MESSAGE, "");

			if (statusCode == null)
			{
				statusCode = "";
			}
			if (detail == null)
			{
				detail = "";
			}

			String summary;
			if (timestamp == 0L && statusCode.isEmpty())
			{
				summary = getString(R.string.remote_telemetry_status_never);
			}
			else
			{
				DateFormat formatter = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
				String formattedTime = timestamp > 0 ? formatter.format(new Date(timestamp)) : "";

				switch (statusCode)
				{
					case RemoteTelemetryManager.STATUS_SUCCESS:
						if (formattedTime.isEmpty())
						{
							summary = getString(R.string.remote_telemetry_status_publish_success);
						}
						else
						{
							summary = getString(R.string.remote_telemetry_status_success, formattedTime);
						}
						break;
					case RemoteTelemetryManager.STATUS_FAILURE:
						String reason = detail.trim().isEmpty()
							? getString(R.string.remote_telemetry_status_error_unknown)
							: detail;
						if (formattedTime.isEmpty())
						{
							summary = reason;
						}
						else
						{
							summary = getString(R.string.remote_telemetry_status_failure, formattedTime, reason);
						}
						break;
					case RemoteTelemetryManager.STATUS_STOPPED:
						if (formattedTime.isEmpty())
						{
							summary = getString(R.string.remote_telemetry_status_stopped);
						}
						else
						{
							summary = getString(R.string.remote_telemetry_status_stopped_at, formattedTime);
						}
						break;
					case RemoteTelemetryManager.STATUS_IDLE:
					default:
						if (formattedTime.isEmpty())
						{
							summary = getString(R.string.remote_telemetry_status_waiting_for_data);
						}
						else
						{
							summary = getString(R.string.remote_telemetry_status_waiting_since, formattedTime);
						}
						break;
				}
			}

			statusPref.setSummary(summary);
		}

		/**
		 * Set up AI features preferences
		 */
		void setupAiFeatures()
		{
			// API key preference
			EditTextPreference apiKeyPref = (EditTextPreference) findPreference("openai_api_key");
			if (apiKeyPref != null)
			{
				// Load existing key from secure storage
				String existingKey = securePreferences.getOpenAiApiKey();
				if (existingKey != null && !existingKey.isEmpty())
				{
					apiKeyPref.setText(existingKey);
					apiKeyPref.setSummary(maskApiKey(existingKey));
				}
				else
				{
					apiKeyPref.setSummary("Not configured - tap to set");
				}

				// Handle preference changes
				apiKeyPref.setOnPreferenceChangeListener((preference, newValue) ->
				{
					String apiKey = String.valueOf(newValue).trim();
					if (apiKey.isEmpty())
					{
						securePreferences.clearOpenAiApiKey();
						apiKeyPref.setSummary("Not configured - tap to set");
					}
					else
					{
						securePreferences.setOpenAiApiKey(apiKey);
						apiKeyPref.setSummary(maskApiKey(apiKey));
						SnackbarHelper.showSuccess(getActivity(), "API key saved securely");
					}
					return true;
				});
			}

			// Info preference
			Preference infoPref = findPreference("openai_api_info");
			if (infoPref != null)
			{
				infoPref.setOnPreferenceClickListener(preference ->
				{
					showApiKeyInfoDialog();
					return true;
				});
			}
		}

		/**
		 * Set up CoPilot features preferences
		 */
		void setupCoPilotFeatures()
		{
			// Delete all conversations preference
			Preference conversationHistoryPref = findPreference("copilot_conversation_history");
			if (conversationHistoryPref != null)
			{
				conversationHistoryPref.setOnPreferenceClickListener(preference ->
				{
					Intent intent = new Intent(getActivity(), com.obddroid.features.copilot.ui.ThreadManagerActivity.class);
					startActivity(intent);
					return true;
				});
			}

			Preference deleteAllPref = findPreference("copilot_delete_all_conversations");
			if (deleteAllPref != null)
			{
				deleteAllPref.setOnPreferenceClickListener(preference ->
				{
					showDeleteAllConversationsDialog();
					return true;
				});
			}
		}

		/**
		 * Shows confirmation dialog for deleting all conversations
		 */
		private void showDeleteAllConversationsDialog()
		{
			new AlertDialog.Builder(requireContext())
				.setTitle("Delete All Conversations?")
				.setMessage("This will permanently delete all stored CoPilot conversation threads from OpenAI's servers.\n\n" +
						"This action cannot be undone.\n\n" +
						"Are you sure you want to continue?")
				.setNegativeButton("Cancel", null)
				.setPositiveButton("Delete All", (dialog, which) ->
				{
					// Import AgentCoPilotController at top of file
					com.obddroid.features.copilot.data.AgentCoPilotController agentController =
						com.obddroid.features.copilot.data.AgentCoPilotController.getInstance();
					agentController.deleteAllConversations();
					SnackbarHelper.showSuccess(getActivity(), "All conversation threads deleted");
				})
				.show();
		}

		/**
		 * Masks the API key for display
		 */
		private String maskApiKey(String apiKey)
		{
			if (apiKey == null || apiKey.length() < 8)
			{
				return "••••••••";
			}
			return apiKey.substring(0, 4) + "••••••••" + apiKey.substring(apiKey.length() - 4);
		}

		/**
		 * Shows a dialog with information about getting an OpenAI API key
		 */
		private void showApiKeyInfoDialog()
		{
			new AlertDialog.Builder(requireContext())
				.setTitle("How to Get an OpenAI API Key")
				.setMessage("To use AI-powered fault code analysis:\n\n" +
						"1. Visit https://platform.openai.com\n" +
						"2. Create a free account or sign in\n" +
						"3. Go to API Keys section\n" +
						"4. Click 'Create new secret key'\n" +
						"5. Copy the key and paste it here\n\n" +
						"Note: Your API key is stored securely on your device and never shared. " +
						"You will be charged by OpenAI based on your usage.")
				.setPositiveButton("OK", null)
				.setNeutralButton("Open Website", (dialog, which) ->
				{
					Intent intent = new Intent(Intent.ACTION_VIEW,
							Uri.parse("https://platform.openai.com/api-keys"));
					try
					{
						startActivity(intent);
					}
					catch (ActivityNotFoundException | SecurityException e)
					{
						log.log(Level.WARNING, "Failed to open OpenAI website", e);
						SnackbarHelper.showError(getActivity(),
								"Could not open browser. Please visit platform.openai.com manually.");
					}
				})
				.show();
		}

		/**
		 * Update fields for network parameters
		 *
		 * enable/disable elements for network parameters
		 * based on selection of communication medium
		 */
		void updateNetworkSelections()
		{
			boolean networkSelected =
				String.valueOf(CommService.MEDIUM.NETWORK.ordinal())
					.equals(prefs.getString(KEY_COMM_MEDIUM,""));
			boolean bluetoothSelected =
				String.valueOf(CommService.MEDIUM.BLUETOOTH.ordinal())
					.equals(prefs.getString(KEY_COMM_MEDIUM,""));
			boolean usbSelected =
					String.valueOf(CommService.MEDIUM.USB.ordinal())
							.equals(prefs.getString(KEY_COMM_MEDIUM,""));

			// enable/disable network specific entries
			for(String key : networkKeys)
			{
				Preference pref = findPreference(key);
				pref.setEnabled(networkSelected);
			}

			// enable/disable bluetooth specific entries
			for(String key : bluetoothKeys)
			{
				Preference pref = findPreference(key);
				pref.setEnabled(bluetoothSelected);
			}

			// enable/disable usb specific entries
			for(String key : usbKeys)
			{
				Preference pref = findPreference(key);
				pref.setEnabled(usbSelected);
			}
		}

		@Override
		public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
		{
			Preference pref = findPreference(key);

			if (pref instanceof ListPreference)
			{
				ListPreference currPref = (ListPreference) pref;
				currPref.setSummary(currPref.getEntry());
			}
			else
			if (pref instanceof EditTextPreference)
			{
				EditTextPreference currPref = (EditTextPreference) pref;
				currPref.setSummary(currPref.getText());
			}

			// Apply full screen changes instantly
			if(MainActivity.PREF_FULLSCREEN.equals(key))
			{
				Toolbar toolbar = getActivity().findViewById(R.id.toolbar);
				if(sharedPreferences.getBoolean(MainActivity.PREF_FULLSCREEN, false))
				{
					// Ultra-dark mode using modern WindowInsetsController
					WindowInsetsControllerCompat windowInsetsController = WindowCompat.getInsetsController(getActivity().getWindow(), getActivity().getWindow().getDecorView());
					if (windowInsetsController != null) {
						windowInsetsController.hide(WindowInsetsCompat.Type.systemBars());
						windowInsetsController.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
					}
					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
						getActivity().getWindow().setNavigationBarColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.background_secondary));
					}
					// Make the toolbar black
					if (toolbar != null) {
						toolbar.setBackgroundColor(Color.BLACK);
					}
				}
				else
				{
					// Restore normal mode
					WindowInsetsControllerCompat windowInsetsController = WindowCompat.getInsetsController(getActivity().getWindow(), getActivity().getWindow().getDecorView());
					if (windowInsetsController != null) {
						windowInsetsController.show(WindowInsetsCompat.Type.systemBars());
					}
					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
						getActivity().getWindow().setNavigationBarColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.background_secondary));
					}
					// Restore toolbar color
					if (toolbar != null) {
						toolbar.setBackgroundColor(Color.parseColor("#212121"));
					}
				}
			}

			if(KEY_COMM_MEDIUM.equals(key))
				updateNetworkSelections();

			if(ELM_TIMING_SELECT.equals(key))
				//noinspection ConstantConditions
				findPreference(ELM_MIN_TIMEOUT)
					.setEnabled(ElmProt.AdaptTimingMode.SOFTWARE.toString()
						          .equals(((ListPreference)pref).getValue())
					           );

			if (RemoteTelemetryManager.PREF_LAST_STATUS_CODE.equals(key)
				|| RemoteTelemetryManager.PREF_LAST_STATUS_TIME.equals(key)
				|| RemoteTelemetryManager.PREF_LAST_STATUS_MESSAGE.equals(key))
			{
				updateRemoteTelemetryStatusPreference();
			}
		}
	}
}
