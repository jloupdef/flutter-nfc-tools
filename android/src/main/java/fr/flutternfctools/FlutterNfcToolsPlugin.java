package fr.flutternfctools;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Application;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.TagLostException;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.nfc.tech.TagTechnology;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry;
import io.flutter.plugin.common.PluginRegistry.Registrar;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@TargetApi(19)
public class FlutterNfcToolsPlugin implements MethodCallHandler, PluginRegistry.NewIntentListener {
  /** Plugin registration. */
  public static void registerWith(Registrar registrar) {
    final MethodChannel methodChannel = new MethodChannel(registrar.messenger(), "flutter_nfc_tools/methods");

		final EventChannel ndef = new EventChannel(registrar.messenger(), "flutter_nfc_tools/eventsNdef");
		final EventChannel ndefFormatable = new EventChannel(registrar.messenger(), "flutter_nfc_tools/eventsNdefFormatable");
		final EventChannel mimetype = new EventChannel(registrar.messenger(), "flutter_nfc_tools/eventsMimetype");
		final EventChannel tag = new EventChannel(registrar.messenger(), "flutter_nfc_tools/eventsTag");

    FlutterNfcToolsPlugin instance = new FlutterNfcToolsPlugin(registrar, ndef, ndefFormatable, mimetype, tag);
    registrar.addNewIntentListener(instance);
    methodChannel.setMethodCallHandler(instance);
  }

  private FlutterNfcToolsPlugin(Registrar registrar,
                      EventChannel ndef,
											EventChannel ndefFormatable,
											EventChannel mimetype,
											EventChannel tag) {
    //nfcManager = (NfcManager)registrar.activity().getSystemService(Context.NFC_SERVICE) ;
    //nfcAdapter = nfcManager.getDefaultAdapter();

    this.activity = registrar.activity();

		initActivityLifecycle();

    activity.getApplication().registerActivityLifecycleCallbacks(this.activityLifecycleCallbacks);

		tag.setStreamHandler(new EventChannel.StreamHandler() {
			@Override public void onListen(Object o, EventChannel.EventSink eventSink) { registerDefaultTag(); tagEventsSink = eventSink; }
			@Override public void onCancel(Object o) { tagEventsSink = null; removeDefaultTag(); }
		});
		ndef.setStreamHandler(new EventChannel.StreamHandler() {
			@Override public void onListen(Object o, EventChannel.EventSink eventSink) { registerNdef(); ndefEventsSink = eventSink; }
			@Override public void onCancel(Object o) { ndefEventsSink = null; removeNdef(); }
		});
		ndefFormatable.setStreamHandler(new EventChannel.StreamHandler() {
			@Override public void onListen(Object o, EventChannel.EventSink eventSink) { registerNdefFormatable(); ndefFormatableEventsSink = eventSink; }
			@Override public void onCancel(Object o) { ndefFormatableEventsSink = null; removeNdefFormatable(); }
		});
		mimetype.setStreamHandler(new EventChannel.StreamHandler() {
			@Override public void onListen(Object o, EventChannel.EventSink eventSink) { mimetypeEventsSink = eventSink; }
			@Override public void onCancel(Object o) { mimetypeEventsSink = null; }
		});
  }

  private final Activity activity;
  
  private Application.ActivityLifecycleCallbacks activityLifecycleCallbacks;


// Phonegap code adapted to flutter starts here, can be directly compared //

	private static final String REGISTER_MIME_TYPE = "registerMimeType";
	private static final String REMOVE_MIME_TYPE = "removeMimeType";
	private static final String REGISTER_NDEF = "registerNdef";
	private static final String REMOVE_NDEF = "removeNdef";
	private static final String REGISTER_NDEF_FORMATABLE = "registerNdefFormatable";
	private static final String REGISTER_DEFAULT_TAG = "registerTag";
	private static final String REMOVE_DEFAULT_TAG = "removeTag";
	private static final String WRITE_TAG = "writeTag";
	private static final String MAKE_READ_ONLY = "makeReadOnly";
	private static final String ERASE_TAG = "eraseTag";
	private static final String ENABLED = "enabled";
	private static final String INIT = "init";
	private static final String SHOW_SETTINGS = "showSettings";
/*
	private static final String NDEF = "ndef";
	private static final String NDEF_MIME = "ndef-mime";
	private static final String NDEF_FORMATABLE = "ndef-formatable";
	private static final String TAG_DEFAULT = "tag";
*/
	// TagTechnology IsoDep, NfcA, NfcB, NfcV, NfcF, MifareClassic, MifareUltralight
	private static final String CONNECT = "connect";
	private static final String CLOSE = "close";

	private TagTechnology tagTechnology = null;
	private Class<?> tagTechnologyClass;

	//private static final String CHANNEL = "channel";

	private static final String STATUS_NFC_OK = "NFC_OK";
	private static final String STATUS_NO_NFC = "NO_NFC";
	private static final String STATUS_NFC_DISABLED = "NFC_DISABLED";
	//private static final String STATUS_NDEF_PUSH_DISABLED = "NDEF_PUSH_DISABLED";

	private static final String TAG = "NfcPlugin";

	private final List<IntentFilter> intentFilters = new ArrayList<>();
	private final ArrayList<String[]> techLists = new ArrayList<>();
	//private NdefMessage p2pMessage = null;

	private PendingIntent pendingIntent = null;

	private Intent savedIntent = null;

	private EventChannel.EventSink tagEventsSink;
	private EventChannel.EventSink ndefEventsSink;
	private EventChannel.EventSink ndefFormatableEventsSink;
	private EventChannel.EventSink mimetypeEventsSink;

  @Override
	//public boolean execute(String action, JSONArray data, Result result) throws JSONException {
	public void onMethodCall(MethodCall call, Result result) {
		String action = call.method;
		Log.d(TAG, "execute " + action);

		// TODO: is it working
		List<?> data = call.arguments();

    // showSettings can be called if NFC is disabled
    // might want to skip this if NO_NFC
    if (action.equalsIgnoreCase(SHOW_SETTINGS)) {
      showSettings();
      return;
    }

    if (!getNfcStatus().equals(STATUS_NFC_OK)) {
      result.error("NFC_STATUS", getNfcStatus(), null);
      return; // short circuit
    }

    createPendingIntent();
		try {
			if (action.equalsIgnoreCase(REGISTER_MIME_TYPE)) {
				registerMimeType(data);

			} else if (action.equalsIgnoreCase(REMOVE_MIME_TYPE)) {
				removeMimeType(data);

			} else if (action.equalsIgnoreCase(REGISTER_NDEF)) {
				registerNdef();

			} else if (action.equalsIgnoreCase(REMOVE_NDEF)) {
				removeNdef();

			} else if (action.equalsIgnoreCase(REGISTER_NDEF_FORMATABLE)) {
				registerNdefFormatable();

			} else if (action.equals(REGISTER_DEFAULT_TAG)) {
				registerDefaultTag();

			} else if (action.equals(REMOVE_DEFAULT_TAG)) {
				removeDefaultTag();

			} else if (action.equalsIgnoreCase(WRITE_TAG)) {
				writeTag(data, result);

			} else if (action.equalsIgnoreCase(MAKE_READ_ONLY)) {
				makeReadOnly(result);

			} else if (action.equalsIgnoreCase(ERASE_TAG)) {
				eraseTag(result);

			} else if (action.equalsIgnoreCase(INIT)) {
				init();

			} else if (action.equalsIgnoreCase(ENABLED)) {
				// status is checked before every call
				// if code made it here, NFC is enabled
				result.success(STATUS_NFC_OK);

			} else if (action.equalsIgnoreCase(CONNECT)) {
				String tech = (String)data.get(0);
				int timeout = data.get(1)!=null ? (Integer)data.get(1) : -1;
				connect(tech, timeout, result);

			} else if (action.equalsIgnoreCase(CLOSE)) {
				close(result);

			} else {
				// invalid action
				result.notImplemented();
			}
		} catch (JSONException e){
			result.error("JSON_EXCEPTION", e.getMessage(), null);
		}
  }

	private String getNfcStatus() {
		NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(getActivity());
		if (nfcAdapter == null) {
			return STATUS_NO_NFC;
		} else if (!nfcAdapter.isEnabled()) {
			return STATUS_NFC_DISABLED;
		} else {
			return STATUS_NFC_OK;
		}
	}

	private void registerDefaultTag() {
		addTagFilter();
		restartNfc();
	}

	private void removeDefaultTag() {
		removeTagFilter();
		restartNfc();
	}

	private void registerNdefFormatable() {
		addTechList(new String[]{NdefFormatable.class.getName()});
		restartNfc();
	}
	private void removeNdefFormatable() {
		removeTechList(new String[]{NdefFormatable.class.getName()});
		restartNfc();
	}

	private void registerNdef() {
		addTechList(new String[]{Ndef.class.getName()});
		restartNfc();
	}

	private void removeNdef() {
		removeTechList(new String[]{Ndef.class.getName()});
		restartNfc();
	}


	private void init() throws JSONException {
		Log.d(TAG, "Enabling plugin " + getIntent());

		startNfc();
		if (!recycledIntent()) {
			parseMessage();
		}
	}

	private void removeMimeType(List<?> data) throws JSONException {
		String mimeType = (String)data.get(0);
		removeIntentFilter(mimeType);
		restartNfc();
	}

	private void registerMimeType(List<?> data) throws JSONException {
		String mimeType = "";
		try {
			mimeType = (String)data.get(0);
			intentFilters.add(createIntentFilter(mimeType));
			restartNfc();
		} catch (IntentFilter.MalformedMimeTypeException e) {
			Log.e(TAG, "Invalid MIME Type " + mimeType, e);
		}
	}

	// Cheating and writing an empty record. We may actually be able to erase some tag types.
	private void eraseTag(Result result) {
		Tag tag = savedIntent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
		NdefRecord[] records = {
				new NdefRecord(NdefRecord.TNF_EMPTY, new byte[0], new byte[0], new byte[0])
		};
		writeNdefMessage(new NdefMessage(records), tag, result);
	}

	private void writeTag(List<?> data, Result result) throws JSONException {
		if (getIntent() == null) {  // TODO remove this and handle LostTag
			result.error("WRITE_NULL_INTENT", "Failed to write tag, received null intent", null);
		}

		Tag tag = savedIntent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
		NdefRecord[] records = Util.jsonToNdefRecords((String)data.get(0));
		writeNdefMessage(new NdefMessage(records), tag, result);
	}

	private void writeNdefMessage(final NdefMessage message, final Tag tag, final Result result) {
		// TODO : Do it the flutter way
		//cordova.getThreadPool().execute(() -> {
			try {
				Ndef ndef = Ndef.get(tag);
				if (ndef != null) {
					ndef.connect();

					if (ndef.isWritable()) {
						int size = message.toByteArray().length;
						if (ndef.getMaxSize() < size) {
							result.error("WRITE_CAPACITY", "Tag capacity is " + ndef.getMaxSize() +
									" bytes, message is " + size + " bytes.", null);
						} else {
							ndef.writeNdefMessage(message);
							result.success(true);
						}
					} else {
						result.error("WRITE_READONLY", "Tag is read only", null);
					}
					ndef.close();
				} else {
					NdefFormatable formatable = NdefFormatable.get(tag);
					if (formatable != null) {
						formatable.connect();
						formatable.format(message);
						result.success(true);
						formatable.close();
					} else {
						result.error("WRITE_NDEF_UNSUPPORTED", "Tag doesn't support NDEF", null);
					}
				}
			} catch (FormatException e) {
				result.error("WRITE_FORMAT_EXCEPTION", e.getMessage(), null);
			} catch (TagLostException e) {
				result.error("WRITE_TAG_LOST_EXCEPTION", e.getMessage(), null);
			} catch (IOException e) {
				result.error("WRITE_IO_EXCEPTION", e.getMessage(), null);
			}
		//});
	}

	private void makeReadOnly(final Result result) {

		if (getIntent() == null) { // Lost Tag
			result.error("MAKE_RO_NULL_INTENT","Failed to make tag read only, received null intent", null);
			return;
		}

		final Tag tag = savedIntent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
		if (tag == null) {
			result.error("MAKE_RO_NULL","Failed to make tag read only, tag is null", null);
			return;
		}

		// TODO : do it the flutter way
		//cordova.getThreadPool().execute(() -> {
			boolean success = false;
			String message = "Could not make tag read only";

			Ndef ndef = Ndef.get(tag);

			try {
				if (ndef != null) {

					ndef.connect();

					if (!ndef.isWritable()) {
						message = "Tag is not writable";
					} else if (ndef.canMakeReadOnly()) {
						success = ndef.makeReadOnly();
					} else {
						message = "Tag can not be made read only";
					}

				} else {
					message = "Tag is not NDEF";
				}

			} catch (IOException e) {
				Log.e(TAG, "Failed to make tag read only", e);
				if (e.getMessage() != null) {
					message = e.getMessage();
				} else {
					message = e.toString();
				}
			}

			if (success) {
				result.success(true);
			} else {
				result.error("MAKE_RO_UNKNOWN", message, null);
			}
		//});
	}


	private void showSettings() {
		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
			Intent intent = new Intent(android.provider.Settings.ACTION_NFC_SETTINGS);
			getActivity().startActivity(intent);
		} else {
			Intent intent = new Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS);
			getActivity().startActivity(intent);
		}
	}

	private void createPendingIntent() {
		if (pendingIntent == null) {
			Activity activity = getActivity();
			Intent intent = new Intent(activity, activity.getClass());
			intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
			pendingIntent = PendingIntent.getActivity(activity, 0, intent, 0);
		}
	}

	private void addTechList(String[] list) {
		this.addTechFilter();
		this.addToTechList(list);
	}

	private void removeTechList(String[] list) {
		this.removeTechFilter();
		this.removeFromTechList(list);
	}

	private void addTechFilter() {
		intentFilters.add(new IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED));
	}

	private void removeTechFilter() {
		Iterator<IntentFilter> iterator = intentFilters.iterator();
		while (iterator.hasNext()) {
			IntentFilter intentFilter = iterator.next();
			if (NfcAdapter.ACTION_TECH_DISCOVERED.equals(intentFilter.getAction(0))) {
				iterator.remove();
			}
		}
	}

	private void addTagFilter() {
		intentFilters.add(new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED));
	}

	private void removeTagFilter() {
		Iterator<IntentFilter> iterator = intentFilters.iterator();
		while (iterator.hasNext()) {
			IntentFilter intentFilter = iterator.next();
			if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(intentFilter.getAction(0))) {
				iterator.remove();
			}
		}
	}

	private void restartNfc() {
		stopNfc();
		startNfc();
	}

	private void startNfc() {
		Log.d(TAG, "startNfc");
		createPendingIntent(); // onResume can call startNfc before execute

		// TODO : do it the flutter way
		//getActivity().runOnUiThread(() -> {
			NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(getActivity());

			if (nfcAdapter != null && !getActivity().isFinishing()) {
				try {
					IntentFilter[] intentFilters = getIntentFilters();
					String[][] techLists = getTechLists();
					// don't start NFC unless some intent filters or tech lists have been added,
					// because empty lists act as wildcards and receives ALL scan events
					if (intentFilters.length > 0 || techLists.length > 0) {
						nfcAdapter.enableForegroundDispatch(getActivity(), getPendingIntent(), intentFilters, techLists);
	/*
						Intent intent = new Intent(activity, activity.getClass());
						intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
						PendingIntent pendingIntent = PendingIntent.getActivity(activity, 0, intent, 0);
						nfcAdapter.enableForegroundDispatch(activity, pendingIntent, intentFilters, techLists);
	*/
					}
					/* // TODO : confirm it is not used except for beam and sharing
					if (p2pMessage != null) {
						nfcAdapter.setNdefPushMessage(p2pMessage, getActivity());
					}*/
				} catch (IllegalStateException e) {
					// issue 110 - user exits app with home button while nfc is initializing
					Log.w(TAG, "Illegal State Exception starting NFC. Assuming application is terminating.");
				}
			}
		//});
	}


	private void stopNfc() {
		Log.d(TAG, "stopNfc");
		// TODO : do it flutter way
		//getActivity().runOnUiThread(() -> {

			NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(getActivity());

			if (nfcAdapter != null) {
				try {
					nfcAdapter.disableForegroundDispatch(getActivity());
				} catch (IllegalStateException e) {
					// issue 125 - user exits app with back button while nfc
					Log.w(TAG, "Illegal State Exception stopping NFC. Assuming application is terminating.");
				}
			}
		//});
	}

	private void addToTechList(String[] techs) {
		techLists.add(techs);
	}

	private void removeFromTechList(String[] techs) {
		Iterator<String[]> iterator = techLists.iterator();
		while (iterator.hasNext()) {
			String[] list = iterator.next();
			if (Arrays.equals(list, techs)) {
				iterator.remove();
			}
		}
	}

	private void removeIntentFilter(String mimeType) {
		Iterator<IntentFilter> iterator = intentFilters.iterator();
		while (iterator.hasNext()) {
			IntentFilter intentFilter = iterator.next();
			String mt = intentFilter.getDataType(0);
			if (mimeType.equals(mt)) {
				iterator.remove();
			}
		}
	}

	private IntentFilter createIntentFilter(String mimeType) throws IntentFilter.MalformedMimeTypeException {
		IntentFilter intentFilter = new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED);
		intentFilter.addDataType(mimeType);
		return intentFilter;
	}
	private PendingIntent getPendingIntent() {
		return pendingIntent;
	}

	private IntentFilter[] getIntentFilters() {
		return intentFilters.toArray(new IntentFilter[intentFilters.size()]);
	}

	private String[][] getTechLists() {
		//noinspection ToArrayCallWithZeroLengthArrayArgument
		return techLists.toArray(new String[0][0]);
	}

	private void parseMessage() throws JSONException {
		// TODO : do it the flutter way
		//cordova.getThreadPool().execute(() -> {
			Log.d(TAG, "parseMessage " + getIntent());
			Intent intent = getIntent();
			String action = intent.getAction();
			Log.d(TAG, "action " + action);
			if (action == null) {
				return;
			}

			Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
			Parcelable[] messages = intent.getParcelableArrayExtra((NfcAdapter.EXTRA_NDEF_MESSAGES));

			if (action.equals(NfcAdapter.ACTION_NDEF_DISCOVERED)) {
				Ndef ndef = Ndef.get(tag);
				fireNdefMimeEvent(ndef, messages);

			} else if (action.equals(NfcAdapter.ACTION_TECH_DISCOVERED)) {
				for (String tagTech : tag.getTechList()) {
					Log.d(TAG, tagTech);
					if (tagTech.equals(NdefFormatable.class.getName())) {
						fireNdefFormatableEvent(tag);
					} else if (tagTech.equals(Ndef.class.getName())) { //
						Ndef ndef = Ndef.get(tag);
						fireNdefEvent(ndef, messages);
					}
				}
			}

			if (action.equals(NfcAdapter.ACTION_TAG_DISCOVERED)) {
				fireTagEvent(tag);
			}

			setIntent(new Intent());
		//});
	}

	// Send the event data through a channel so the JavaScript side can fire the event
	/*
	private void sendEvent(String type, JSONObject tag) {

		try {
			JSONObject event = new JSONObject();
			event.put("type", type);       // TAG_DEFAULT, NDEF, NDEF_MIME, NDEF_FORMATABLE
			event.put("tag", tag);         // JSON representing the NFC tag and NDEF messages

			// replace with flutter logic ?
	//		PluginResult result = new PluginResult(PluginResult.Status.OK, event);
	//		result.setKeepCallback(true);
	//		channelCallback.sendPluginResult(result);
		} catch (JSONException e) {
			Log.e(TAG, "Error sending NFC event through the channel", e);
		}
	}*/

	private void fireNdefMimeEvent(Ndef ndef, Parcelable[] messages) throws JSONException {
		if (mimetypeEventsSink != null) {
			JSONObject json = buildNdefJSON(ndef, messages);
			mimetypeEventsSink.success(Util.jsonToMap(json));
		}
	}

	private void fireNdefEvent(Ndef ndef, Parcelable[] messages) throws JSONException {
		if (ndefEventsSink != null) {
			JSONObject json = buildNdefJSON(ndef, messages);
			ndefEventsSink.success(Util.jsonToMap(json));
		}
	}

	private void fireNdefFormatableEvent(Tag tag) throws JSONException {
		if (ndefFormatableEventsSink != null) {
			JSONObject json = Util.tagToJSON(tag);
			ndefFormatableEventsSink.success(Util.jsonToMap(json));
		}
	}

	private void fireTagEvent(Tag tag) throws JSONException {
		if (tagEventsSink != null) {
			JSONObject json = Util.tagToJSON(tag);
			tagEventsSink.success(Util.jsonToMap(json));
		}
	}

	private JSONObject buildNdefJSON(Ndef ndef, Parcelable[] messages) {

		JSONObject json = Util.ndefToJSON(ndef);

		// ndef is null for peer-to-peer
		// ndef and messages are null for ndef format-able
		if (ndef == null && messages != null) {

			try {

				if (messages.length > 0) {
					NdefMessage message = (NdefMessage) messages[0];
					json.put("ndefMessage", Util.messageToJSON(message));
					// guessing type, would prefer a more definitive way to determine type
					json.put("type", "NDEF Push Protocol");
				}

				if (messages.length > 1) {
					Log.wtf(TAG, "Expected one ndefMessage but found " + messages.length);
				}

			} catch (JSONException e) {
				// shouldn't happen
				Log.e(Util.TAG, "Failed to convert ndefMessage into json", e);
			}
		}
		return json;
	}


	private boolean recycledIntent() { // TODO this is a kludge, find real solution
		Intent intent = getIntent();
		if (intent != null) {
			int flags = intent.getFlags();
			if ((flags & Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) {
				Log.i(TAG, "Launched from history, killing recycled intent");
				setIntent(new Intent());
				return true;
			}
		}
		return false;
	}
	private void initActivityLifecycle() {

		this.activityLifecycleCallbacks = new Application.ActivityLifecycleCallbacks() {
			@Override
			public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
				if (activity == FlutterNfcToolsPlugin.this.activity) {
					Log.d(TAG, "onActivityCreated");
				}
			}

			@Override
			public void onActivityStarted(Activity activity) {
				if (activity == FlutterNfcToolsPlugin.this.activity) {
					Log.d(TAG, "onActivityStarted");
				}
			}

			@Override
			public void onActivityResumed(Activity activity) {
				if (activity == FlutterNfcToolsPlugin.this.activity) {
					Log.d(TAG, "onActivityResumed");
					startNfc();
				}
			}

			@Override
			public void onActivityPaused(Activity activity) {
				if (activity == FlutterNfcToolsPlugin.this.activity) {
					Log.d(TAG, "onActivityPaused");
					// TODO : do it the flutter way
					//if (multitasking) {
						// nfc can't run in background
						stopNfc();
					//}
				}
			}

			@Override
			public void onActivityStopped(Activity activity) {
				if (activity == FlutterNfcToolsPlugin.this.activity) {
					Log.d(TAG, "onActivityStopped");
				}
			}

			@Override
			public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
				if (activity == FlutterNfcToolsPlugin.this.activity) {
					Log.d(TAG, "onActivitySaveInstanceState");
				}}

			@Override
			public void onActivityDestroyed(Activity activity) {
				if (activity == FlutterNfcToolsPlugin.this.activity) {
					Log.d(TAG, "onActivitySaveInstanceState");
				}
			}
		};
	}

	@Override
	public boolean onNewIntent(Intent intent) {
		Log.d(TAG, "onNewIntent " + intent);
		// TODO : confirm we do not care
		//super.onNewIntent(intent);

		setIntent(intent);
		savedIntent = intent;
		try {
			parseMessage();
		}catch(JSONException e){
			Log.e(TAG, "JSONException", e);
		}
		return true;
	}

	private Activity getActivity() {
		return activity;
	}

	private Intent getIntent() {
		return getActivity().getIntent();
	}

	private void setIntent(Intent intent) {
		getActivity().setIntent(intent);
	}

	private void connect(final String tech, final int timeout, final Result result) {
			// TODO: do it the flutter way
		// this.cordova.getThreadPool().execute(() -> {
			try {

				Tag tag = getIntent().getParcelableExtra(NfcAdapter.EXTRA_TAG);
				if (tag == null) {
					tag = savedIntent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
				}

				if (tag == null) {
					Log.e(TAG, "No Tag");
					result.error("TAG_NULL", "No Tag", null);
					return;
				}

				// get technologies supported by this tag
				List<String> techList = Arrays.asList(tag.getTechList());
				if (techList.contains(tech)) {
					// use reflection to call the static function Tech.get(tag)
					tagTechnologyClass = Class.forName(tech);
					Method method = tagTechnologyClass.getMethod("get", Tag.class);
					tagTechnology = (TagTechnology) method.invoke(null, tag);
				}

				if (tagTechnology == null) {
					result.error("CONNECT_EXCEPTION", "Tag does not support " + tech, null);
					return;
				}

				tagTechnology.connect();
				setTimeout(timeout);
				result.success(true);

			} catch (IOException ex) {
				Log.e(TAG, "Tag connection failed", ex);
				result.error("CONNECT_EXCEPTION", "IOException : Tag connection failed", null);

				// Users should never get these reflection errors
			} catch (ClassNotFoundException e) {
				Log.e(TAG, e.getMessage(), e);
				result.error("CONNECT_EXCEPTION", e.getMessage(), null);
			} catch (NoSuchMethodException e) {
				Log.e(TAG, e.getMessage(), e);
				result.error("CONNECT_EXCEPTION", e.getMessage(), null);
			} catch (IllegalAccessException e) {
				Log.e(TAG, e.getMessage(), e);
				result.error("CONNECT_EXCEPTION", e.getMessage(), null);
			} catch (InvocationTargetException e) {
				Log.e(TAG, e.getMessage(), e);
				result.error("CONNECT_EXCEPTION", e.getMessage(), null);
			}
		//});
	}

	private void setTimeout(int timeout) {
		if (timeout < 0) {
			return;
		}
		try {
			Method setTimeout = tagTechnologyClass.getMethod("setTimeout", int.class);
			setTimeout.invoke(tagTechnology, timeout);
		} catch (NoSuchMethodException e) {
			// ignore
		} catch (IllegalAccessException e) {
			// ignore
		} catch (InvocationTargetException e) {
			// ignore
		}
	}

	private void close(Result result) {
			// TODO: do it the flutter way
		// cordova.getThreadPool().execute(() -> {
			try {

				if (tagTechnology != null && tagTechnology.isConnected()) {
					tagTechnology.close();
					tagTechnology = null;
					result.success(true);
				} else {
					// connection already gone
					result.success(true);
				}

			} catch (IOException ex) {
				Log.e(TAG, "Error closing nfc connection", ex);
				result.error("CLOSE_EXCEPTION", "Error closing nfc connection " + ex.getLocalizedMessage(), null);
			}
		//});
	}

}
