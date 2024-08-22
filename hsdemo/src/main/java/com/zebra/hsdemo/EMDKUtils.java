package com.zebra.hsdemo;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;
import android.util.Xml;

import com.symbol.emdk.EMDKBase;
import com.symbol.emdk.EMDKException;
import com.symbol.emdk.EMDKManager;
import com.symbol.emdk.EMDKResults;
import com.symbol.emdk.ProfileManager;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.StringReader;
import java.util.ArrayList;

public class EMDKUtils {

    public interface IResultCallbacks{
        void onSuccess(final String message, final String resultXML);
        void onError(final String message, final String resultXML);
        void onDebugStatus(final String message);
    }

    protected enum EMessageType {
        VERBOSE("VERBOSE"),
        WARNING("WARNING"),
        ERROR("ERROR"),
        SUCCESS("SUCCESS"),
        DEBUG("DEBUG");

        String stringContent = "";
        EMessageType(String stringContent) { this.stringContent = stringContent;}

        public String toString() {
            return stringContent;
        }

        public static EMessageType fromString(String messageType)
        {
            switch(messageType) {
                case "VERBOSE":
                    return VERBOSE;
                case "WARNING":
                    return WARNING;
                case "Error":
                    return ERROR;
                case "SUCCESS":
                    return SUCCESS;
                case "DEBUG":
                    return DEBUG;
                default:
                    return null;
            }
        }
    }

    // A class that will hold errors if it happens
    protected class ErrorHolder
    {
        // Provides the error type for characteristic-error
        protected String sErrorType = "";

        // Provides the parm name for parm-error
        protected String sParmName = "";

        // Provides error description
        protected String sErrorDescription = "";
    }

    private final static String TAG = "EMDKUtils";

    // Profile content in XML
    private String msProfileData = "";

    // Profile name to execute
    private String msProfileName = "";

    //Declare a variable to store ProfileManager object
    private ProfileManager mProfileManager = null;

    //Declare a variable to store EMDKManager object
    private EMDKManager mEMDKManager = null;

    // An ArrayList that will contains errors if we find some
    private ArrayList<ErrorHolder> mErrors = new ArrayList<>();

    // Error String
    private String msErrorString = null;

    // Status returned by the profile in case of success
    private String msStatusXMLResponse = "";

    private Context mContext = null;

    private IResultCallbacks mIResultCallbacks = null;

    // EMDKListener implementation
    private EMDKManager.EMDKListener mEMDKListener = new EMDKManager.EMDKListener() {
        @Override
        public void onOpened(EMDKManager emdkManager) {
            logMessage("EMDKManager.EMDKListener.onOpened", EMessageType.DEBUG);
            onEMDKManagerRetrieved(emdkManager);
        }

        @Override
        public void onClosed() {
            logMessage("EMDKManager.EMDKListener.onClosed", EMessageType.DEBUG);
            onEMDKManagerClosed();
        }
    };

    // Status Listener implementation (ensure that we retrieve the profile manager asynchronously
    private EMDKManager.StatusListener mStatusListener = new EMDKManager.StatusListener() {
        @Override
        public void onStatus(EMDKManager.StatusData statusData, EMDKBase emdkBase) {
            if(statusData.getResult() == EMDKResults.STATUS_CODE.SUCCESS)
            {
                ProfileManager profileManager = (ProfileManager)emdkBase;
                if(profileManager != null)
                    onProfileManagerInitialized(profileManager);
                else
                {
                    logMessage("Casting error when retrieving ProfileManager.", EMessageType.ERROR);
                    profileManager = (ProfileManager) mEMDKManager.getInstance(EMDKManager.FEATURE_TYPE.PROFILE);
                    if(profileManager != null) {
                        logMessage("Profile manager retrieved synchronously with success", EMessageType.VERBOSE);
                        onProfileManagerInitialized(profileManager);
                    }
                }
            }
            else
            {
                String errorMessage = "Error when trying to retrieve ProfileManager: " + getResultCode(statusData.getResult());
                logMessage(errorMessage, EMessageType.ERROR);
            }
        }
    };

    public EMDKUtils(Context context)
    {
        mContext = context;
    }

    public void activateVolumeProfile(String audioProfileName, IResultCallbacks resultCallbacks)
    {
        mIResultCallbacks = resultCallbacks;
        // Create profile content
        msProfileName = "AudioVolumeMgr-1";
        msProfileData = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<characteristic type=\"Profile\">\n" +
                "<parm name=\"ProfileName\" value=\"" + msProfileName + "\"/>\n" +
                "<characteristic type=\"AudioVolUIMgr\" version=\"11.6\">\n" +
                "<parm name=\"CurrentProfileAction\" value=\"1\" />\n" +
                "    <characteristic type=\"CurrentUIProfile\">\n" +
                "      <parm name=\"CurrentProfileName\" value=\"" + audioProfileName + "\" />\n" +
                "      <parm name=\"SetCurrentProfileOption\" value=\"2\" />\n" +
                "    </characteristic>\n" +
                "</characteristic>\n" +
                "</characteristic>\n";

        // initialize profile manager processing
        initializeEMDK();
    }

    private void initializeEMDK()
    {
        if(mEMDKManager == null)
        {
            EMDKResults results = null;
            try
            {
                //The EMDKManager object will be created and returned in the callback.
                results = EMDKManager.getEMDKManager(mContext.getApplicationContext(), mEMDKListener);
            }
            catch(Exception e)
            {
                logMessage("Error while requesting EMDKManager.\n" + e.getLocalizedMessage(), EMessageType.ERROR);
                e.printStackTrace();
                return;
            }

            //Check the return status of EMDKManager object creation.
            if(results.statusCode == EMDKResults.STATUS_CODE.SUCCESS) {
                logMessage("EMDKManager request command issued with success", EMessageType.DEBUG);
            }else {
                logMessage("EMDKManager request command error", EMessageType.ERROR);
            }
        }
        else
        {
            onEMDKManagerRetrieved(mEMDKManager);
        }
    }

    private void onEMDKManagerRetrieved(EMDKManager emdkManager)
    {
        mEMDKManager = emdkManager;
        logMessage("EMDK Manager retrieved.", EMessageType.DEBUG);
        if(mProfileManager == null)
        {
            try {
                logMessage("Requesting profile manager.", EMessageType.DEBUG);
                logMessage("Current API version: " + android.os.Build.VERSION.SDK_INT, EMessageType.VERBOSE);
                if(android.os.Build.VERSION.SDK_INT < 33) {
                    logMessage("Requesting profile manager Asynchonously", EMessageType.DEBUG);
                    emdkManager.getInstanceAsync(EMDKManager.FEATURE_TYPE.PROFILE, mStatusListener);
                }
                else
                {
                    logMessage("Requesting profile manager synchronized", EMessageType.DEBUG);
                    ProfileManager profileManager = (ProfileManager) emdkManager.getInstance(EMDKManager.FEATURE_TYPE.PROFILE);
                    if(profileManager != null)
                    {
                        onProfileManagerInitialized(profileManager);
                    }
                }
            } catch (EMDKException e) {
                logMessage("Error when trying to retrieve profile manager: " + e.getMessage(), EMessageType.ERROR);
            }
        }
        else
        {
            logMessage("EMDK Manager already initialized.", EMessageType.DEBUG);
            onProfileManagerInitialized(mProfileManager);
        }
    }

    private void onProfileManagerInitialized(ProfileManager profileManager)
    {
        mProfileManager = profileManager;
        logMessage("Profile Manager retrieved.", EMessageType.DEBUG);
        processMXContent();
    }

    private void processMXContent()
    {
        String[] params = new String[1];
        params[0] = msProfileData;

        if(mProfileManager == null)
        {
            logMessage("ProcessMXContent : Error : ProfileManager == null", EMessageType.ERROR);
            if(mEMDKManager != null) {
                logMessage("ProcessMXContent : Trying to retrieve profileManager synchronously", EMessageType.ERROR);
                ProfileManager profileManager = (ProfileManager) mEMDKManager.getInstance(EMDKManager.FEATURE_TYPE.PROFILE);
                if (profileManager != null) {
                    logMessage("ProcessMXContent, ProfileManager retrieved syncrhonously.", EMessageType.VERBOSE);
                    mProfileManager = profileManager;
                }
                else
                {
                    logMessage("ProcessMXContent : Error : Could not retrieve ProfileManager syncrhonously.", EMessageType.VERBOSE);
                    onProfileExecutedError("ProcessMXContent : Error : Could not retrieve ProfileManager syncrhonously.");
                    return;
                }
            }
            else {
                logMessage("ProcessMXContent : Error : mEMDKManager == null", EMessageType.ERROR);
                onProfileExecutedError("ProcessMXContent : Error : mEMDKManager == null");
                return;
            }
        }

        EMDKResults results = mProfileManager.processProfile(msProfileName, ProfileManager.PROFILE_FLAG.SET, params);

        //Check the return status of processProfile
        if(results.statusCode == EMDKResults.STATUS_CODE.CHECK_XML) {

            // Get XML response as a String
            msStatusXMLResponse = results.getStatusString();

            try {
                // Empty Error Holder Array List if it already exists
                mErrors.clear();

                // Create instance of XML Pull Parser to parse the response
                XmlPullParser parser = Xml.newPullParser();
                // Provide the string response to the String Reader that reads
                // for the parser
                parser.setInput(new StringReader(msStatusXMLResponse));
                // Call method to parse the response
                parseXML(parser);

                if ( mErrors.size() == 0 ) {

                    logMessage("Profile executed with success: " + msProfileName, EMessageType.SUCCESS);
                    onProfileExecutedWithSuccess();
                }
                else {
                    String errorMessage = "";
                    for(ErrorHolder error : mErrors)
                    {
                        errorMessage += "Profile processing error.\t" + "Type:" + error.sErrorType + "\tParamName:" + error.sParmName + "\tDescription:" + error.sErrorDescription;
                    }
                    logMessage(errorMessage, EMessageType.ERROR);
                    onProfileExecutedError(errorMessage);
                    return;
                }

            } catch (XmlPullParserException e) {
                String errorMessage = "Error while trying to parse ProfileManager XML Response: " + e.getLocalizedMessage();
                logMessage(errorMessage, EMessageType.ERROR);
                onProfileExecutedError(errorMessage);
                return;
            }
        }
        else if(results.statusCode == EMDKResults.STATUS_CODE.SUCCESS)
        {
            logMessage("Profile executed with success: " + msProfileName, EMessageType.DEBUG);
            onProfileExecutedWithSuccess();
            return;
        }
        else
        {
            String errorMessage = "Profile update failed." + getResultCode(results.statusCode) + "\nProfil:\n" + msProfileName;
            logMessage(errorMessage, EMessageType.ERROR);
            onProfileExecutedError(errorMessage);
            return;
        }
    }

    private void onProfileExecutedWithSuccess()
    {
        cleanUp();
        if(mIResultCallbacks != null)
        {
            mIResultCallbacks.onSuccess("Success applying profile:" + msProfileName + "\nProfileData:" + msProfileData, msStatusXMLResponse);
        }

    }

    private void onProfileExecutedError(String message)
    {
        cleanUp();
        if(mIResultCallbacks != null)
        {
            mIResultCallbacks.onError("Error on profile: " + msProfileName + "\nError:" + message + "\nProfileData:" + msProfileData, msStatusXMLResponse);
        }

    }

    private void onProfileExecutedStatusChanged(String message)
    {
        if(mIResultCallbacks != null)
        {
            mIResultCallbacks.onDebugStatus(message);
        }
    }

    // Method to parse the XML response using XML Pull Parser
    private void parseXML(XmlPullParser myParser) {
        int event;
        try {
            // Retrieve error details if parm-error/characteristic-error in the response XML
            event = myParser.getEventType();
            // An object that will store a temporary error holder if an error characteristic is found
           ErrorHolder tempErrorHolder = null;
            //logMessage("XML document", EMessageType.VERBOSE);
            while (event != XmlPullParser.END_DOCUMENT) {
                String name = myParser.getName();
                switch (event) {
                    case XmlPullParser.START_TAG:
                        //logMessage("XML Element:<" + myParser.getText()+">", EMessageType.VERBOSE);
                        if (name.equals("characteristic-error"))
                        {
                            if(tempErrorHolder == null)
                                tempErrorHolder = new ErrorHolder();
                            tempErrorHolder.sErrorType = myParser.getAttributeValue(null, "type");
                            if(tempErrorHolder.sParmName != null && TextUtils.isEmpty(tempErrorHolder.sParmName) == false)
                            {
                                msErrorString += "Nom: " + tempErrorHolder.sParmName + "\nType: " + tempErrorHolder.sErrorType + "\nDescription: " + tempErrorHolder.sErrorDescription + ")";
                                mErrors.add(tempErrorHolder);
                                tempErrorHolder = null;
                            }
                        }
                        else if (name.equals("parm-error"))
                        {
                            if(tempErrorHolder == null)
                                tempErrorHolder = new ErrorHolder();
                            tempErrorHolder.sParmName = myParser.getAttributeValue(null, "name");
                            tempErrorHolder.sErrorDescription = myParser.getAttributeValue(null, "desc");
                            if(tempErrorHolder.sErrorType != null && TextUtils.isEmpty(tempErrorHolder.sErrorType) == false)
                            {
                                msErrorString += "Nom: " + tempErrorHolder.sParmName + "\nType: " + tempErrorHolder.sErrorType + "\nDescription: " + tempErrorHolder.sErrorDescription + ")";
                                mErrors.add(tempErrorHolder);
                                tempErrorHolder = null;
                            }
                        }
                        break;
                    case XmlPullParser.END_TAG:
                        //logMessage("XML Element:<//" + myParser.getText()+">", EMessageType.VERBOSE);
                        break;
                }
                event = myParser.next();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void onEMDKManagerClosed()
    {
        cleanUp();
    }

    private void cleanUp()
    {
        if(mProfileManager != null)
        {
            mProfileManager = null;
            logMessage("Profile Manager reseted.", EMessageType.DEBUG);
        }

        //This callback will be issued when the EMDK closes unexpectedly.
        if (mEMDKManager != null) {
            mEMDKManager.release();
            logMessage("EMDKManager released.", EMessageType.DEBUG);
            mEMDKManager = null;
            logMessage("EMDKManager reseted.", EMessageType.DEBUG);
        }
    }


    private void logMessage(String message, EMessageType messageType)
    {
        switch(messageType)
        {
            case ERROR:
                Log.e(TAG, message);
                onProfileExecutedStatusChanged("ERROR:" + message);
                break;
            case SUCCESS:
                Log.v(TAG, message);
                onProfileExecutedStatusChanged("SUCCESS:" + message);
                break;
            case VERBOSE:
                Log.v(TAG, message);
                onProfileExecutedStatusChanged("VERBOSE:" + message);
                break;
            case WARNING:
                Log.w(TAG, message);
                onProfileExecutedStatusChanged("WARNING:" + message);
                break;
            case DEBUG:
                Log.d(TAG,message);
                onProfileExecutedStatusChanged("DEBUG:" + message);
        }
    }

    private String getResultCode(EMDKResults.STATUS_CODE aStatusCode)
    {
        switch (aStatusCode)
        {
            case FAILURE:
                return "FAILURE";
            case NULL_POINTER:
                return "NULL_POINTER";
            case EMPTY_PROFILENAME:
                return "EMPTY_PROFILENAME";
            case EMDK_NOT_OPENED:
                return "EMDK_NOT_OPENED";
            case CHECK_XML:
                return "CHECK_XML";
            case PREVIOUS_REQUEST_IN_PROGRESS:
                return "PREVIOUS_REQUEST_IN_PROGRESS";
            case PROCESSING:
                return "PROCESSING";
            case NO_DATA_LISTENER:
                return "NO_DATA_LISTENER";
            case FEATURE_NOT_READY_TO_USE:
                return "FEATURE_NOT_READY_TO_USE";
            case FEATURE_NOT_SUPPORTED:
                return "FEATURE_NOT_SUPPORTED";
            case UNKNOWN:
            default:
                return "UNKNOWN";
        }
    }
}
