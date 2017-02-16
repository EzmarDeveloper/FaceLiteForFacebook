/*
SlimSocial for Facebook is an Open Source app realized by Leonardo Rignanese
 GNU GENERAL PUBLIC LICENSE  Version 2, June 1991


!!!!!!!!!!!!!!! Special thanks to https://github.com/indywidualny/FaceSlim !!!!!!!!!!!!!!!!!
!!!!!!!!!!!!!!!!!!!!!!!!!!!! I've token some inspiration an code from their work!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
*/

package net.ezmar.facelite;

import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.*;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Toast;
import android.app.Activity;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.InterstitialAd;
import com.mobfox.sdk.bannerads.Banner;
import com.mobfox.sdk.bannerads.BannerListener;
import java.lang.ref.WeakReference;
import im.delight.android.webview.AdvancedWebView;

public class MainActivity extends Activity implements AdvancedWebView.Listener {

    private Banner banner;
    private AdView mAdView;
    private String appHash = "3e1014b9d3482030ee69797201a34da9";
    private String adMobInter = "ca-app-pub-4889375749088974/2272489842";
    private SwipeRefreshLayout swipeRefreshLayout;//the layout that allows the swipe refresh
    private AdvancedWebView webViewFacebook;//the main webView where is shown facebook
    private Menu optionsMenu;//contains the main menu
    private SharedPreferences savedPreferences;//contains all the values of saved preferences
    private boolean noConnectionError = false;//flag: is true if there is a connection error and it should be reload not the error page but the last useful
    private boolean isSharer = false;//flag: true if the app is called from sharer
    private String urlSharer = "";//to save the url got from the sharer
    private final MyHandler linkHandler = new MyHandler(this); // create link handler (long clicked links)

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        savedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        SetTheme();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (savedPreferences.getBoolean("first_run", true)) {
            savedPreferences.edit().putBoolean("first_run", false).apply();
        }
        SetupMobfox();
        AdMobLoad();
        SetupRefreshLayout();
        ShareLinkHandler();
        SetupWebView();
        SetupOnLongClickListener();

        if (isSharer) {//if is a share request
            webViewFacebook.loadUrl(urlSharer);
            isSharer = false;
        }
        else if (getIntent() != null && getIntent().getDataString() != null) {
            webViewFacebook.loadUrl(getIntent().getDataString());
        } else GoHome();//load homepage



    }

    //need to add this so video ads will work properly
    @Override
    protected void onPause() {
        super.onPause();
        banner.onPause();
        webViewFacebook.onPause();

    }

    @Override
    protected void onResume() {
        super.onResume();
        banner.onResume();
        webViewFacebook.onResume();
    }

    @Override
    protected void onDestroy() {
        if (interstitial.isLoaded()){
            interstitial.show();
        }
        super.onDestroy();
        webViewFacebook.onDestroy();
    }

    private void SetupRefreshLayout() {
        swipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipe_container);
        swipeRefreshLayout.setColorSchemeResources(R.color.officialBlueFacebook, R.color.darkBlueSlimFacebookTheme);// set the colors
        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                RefreshPage();//reload the page
            }
        });
    }

    // app is already running and gets a new intent (used to share link without open another activity
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);

        // grab an url if opened by clicking a link
        String webViewUrl = getIntent().getDataString();

        /** get a subject and text and check if this is a link trying to be shared */
        String sharedSubject = getIntent().getStringExtra(Intent.EXTRA_SUBJECT);
        String sharedUrl = getIntent().getStringExtra(Intent.EXTRA_TEXT);
        Log.d("sharedUrl", "onNewIntent() - sharedUrl: " + sharedUrl);
        // if we have a valid URL that was shared by us, open the sharer
        if (sharedUrl != null) {
            if (!sharedUrl.equals("")) {
                // check if the URL being shared is a proper web URL
                if (!sharedUrl.startsWith("http://") || !sharedUrl.startsWith("https://")) {
                    // if it's not, let's see if it includes an URL in it (prefixed with a message)
                    int startUrlIndex = sharedUrl.indexOf("http:");
                    if (startUrlIndex > 0) {
                        // seems like it's prefixed with a message, let's trim the start and get the URL only
                        sharedUrl = sharedUrl.substring(startUrlIndex);
                    }
                }
                // final step, set the proper Sharer...
                webViewUrl = String.format("https://m.facebook.com/sharer.php?u=%s&t=%s", sharedUrl, sharedSubject);
                // ... and parse it just in case
                webViewUrl = Uri.parse(webViewUrl).toString();
            }
        }
        webViewFacebook.loadUrl(webViewUrl);

        // recreate activity when something important was just changed
        if (getIntent().getBooleanExtra("settingsChanged", false)) {
            finish(); // close this
            Intent restart = new Intent(MainActivity.this, MainActivity.class);
            startActivity(restart);//reopen this
        }
    }

    private void SetupWebView() {
        webViewFacebook = (AdvancedWebView)findViewById(R.id.webView);
        webViewFacebook.setListener(this, this);
        webViewFacebook.clearPermittedHostnames();
        webViewFacebook.addPermittedHostname("facebook.com");
        webViewFacebook.addPermittedHostname("fbcdn.net");
        webViewFacebook.requestFocus(View.FOCUS_DOWN);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        WebSettings settings = webViewFacebook.getSettings();
        settings.setJavaScriptEnabled(true);
        int zoom = Integer.parseInt(savedPreferences.getString("pref_textSize", "100"));
        settings.setTextZoom(zoom);
        settings.setGeolocationEnabled(savedPreferences.getBoolean("pref_allowGeolocation", true));
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportZoom(true);
        settings.setDisplayZoomControls(false);
        settings.setBuiltInZoomControls(true);
        settings.setAppCachePath(getCacheDir().getAbsolutePath());
        settings.setAppCacheEnabled(true);
        settings.setLoadsImagesAutomatically(!savedPreferences.getBoolean("pref_doNotDownloadImages", false));
        if(Build.VERSION.SDK_INT > Build.VERSION_CODES.HONEYCOMB) {
            settings.setDisplayZoomControls(false);
        }
    }

    //*********************** UPLOAD FILES ****************************
    //!!!!!!!!!!! thanks to FaceSlim !!!!!!!!!!!!!!!
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        webViewFacebook.onActivityResult(requestCode, resultCode, intent);
    }

    private void SetTheme() {
        switch (savedPreferences.getString("pref_theme", "default")) {
            case "DarkTheme": {
                setTheme(R.style.DarkTheme);
                break;
            }
            default: {
                setTheme(R.style.DefaultTheme);
                break;
            }
        }
    }

    //*********************** MENU ****************************
    //add my menu
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        optionsMenu = menu;
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    //management the tap on the menu's items
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case R.id.top: {//scroll on the top of the page
                webViewFacebook.scrollTo(0, 0);
                break;
            }
            case R.id.openInBrowser: {//open the actual page into using the browser
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(webViewFacebook.getUrl())));
                break;
            }
            case R.id.messages: {//open messages
                startActivity(new Intent(this, MessagesActivity.class));
                break;
            }
            case R.id.refresh: {//refresh the page
                RefreshPage();
                break;
            }
            case R.id.home: {//go to the home
                GoHome();
                break;
            }
            case R.id.shareLink: {//share this page
                Intent sharingIntent = new Intent(android.content.Intent.ACTION_SEND);
                sharingIntent.setType("text/plain");
                sharingIntent.putExtra(android.content.Intent.EXTRA_TEXT, MyHandler.cleanUrl(webViewFacebook.getUrl()));
                startActivity(Intent.createChooser(sharingIntent, getResources().getString(R.string.shareThisLink)));

                break;
            }
            case R.id.share: {//share this app
                Intent sharingIntent = new Intent(android.content.Intent.ACTION_SEND);
                sharingIntent.setType("text/plain");
                sharingIntent.putExtra(android.content.Intent.EXTRA_TEXT, getResources().getString(R.string.downloadThisApp));
                startActivity(Intent.createChooser(sharingIntent, getResources().getString(R.string.share)));

                Toast.makeText(getApplicationContext(), getResources().getString(R.string.thanks),
                        Toast.LENGTH_SHORT).show();
                break;
            }
            case R.id.settings: {//open settings
                startActivity(new Intent(this, ShowSettingsActivity.class));
                return true;
            }

            case R.id.exit: {//open settings
                android.os.Process.killProcess(android.os.Process.myPid());
                System.exit(1);
                return true;
            }

            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }


    //*********************** WEBVIEW FACILITIES ****************************
    private void GoHome() {
        if (savedPreferences.getBoolean("pref_recentNewsFirst", false)) {
            webViewFacebook.loadUrl(getString(R.string.urlFacebookMobile) + "?sk=h_chr");
        } else {
            webViewFacebook.loadUrl(getString(R.string.urlFacebookMobile) + "?sk=h_nor");
        }
    }

    private void RefreshPage() {
        if (noConnectionError) {
            webViewFacebook.goBack();
            noConnectionError = false;
        } else webViewFacebook.reload();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        banner.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private void ApplyCustomCss() {
        String css = "";
        if (savedPreferences.getBoolean("pref_centerTextPosts", false)) {
            css += getString(R.string.centerTextPosts);
        }
        if (savedPreferences.getBoolean("pref_addSpaceBetweenPosts", false)) {
            css += getString(R.string.addSpaceBetweenPosts);
        }
        if (savedPreferences.getBoolean("pref_hideSponsoredPosts", false)) {
            css += getString(R.string.hideAdsAndPeopleYouMayKnow);
        }
        if (savedPreferences.getBoolean("pref_fixedBar", true)) {//without add the barHeight doesn't scroll
            css += (getString(R.string.fixedBar).replace("$s", "" + Dimension.heightForFixedFacebookNavbar(getApplicationContext())));
        }
        if (savedPreferences.getBoolean("pref_removeMessengerDownload", true)) {
            css += getString(R.string.removeMessengerDownload);
        }

        switch (savedPreferences.getString("pref_theme", "standard")) {
            case "DarkTheme":
            case "DarkNoBar": {
                css += getString(R.string.blackTheme);
            }
            default:
                break;
        }

        //apply the customizations
        webViewFacebook.loadUrl(getString(R.string.editCss).replace("$css", css));
    }

    //*********************** OTHER ****************************

    // handle long clicks on links, an awesome way to avoid memory leaks
    private static class MyHandler extends Handler {
        MainActivity activity;
        private final WeakReference<MainActivity> mActivity;
        MyHandler(MainActivity activity) {
            this.activity = activity;
            mActivity = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            SharedPreferences savedPreferences = PreferenceManager.getDefaultSharedPreferences(activity);
            if(savedPreferences.getBoolean("pref_enableFastShare", true)) {
                MainActivity activity = mActivity.get();
                if (activity != null) {
                    // get url to share
                    String url = (String) msg.getData().get("url");
                    if (url != null) {
                    /* "clean" an url to remove Facebook tracking redirection while sharing
                    and recreate all the special characters */
                        url = decodeUrl(cleanUrl(url));
                        // create share intent for long clicked url
                        Intent intent = new Intent(Intent.ACTION_SEND);
                        intent.setType("text/plain");
                        intent.putExtra(Intent.EXTRA_TEXT, url);
                        activity.startActivity(Intent.createChooser(intent, activity.getString(R.string.shareThisLink)));
                    }
                }
            }
        }

        // "clean" an url and remove Facebook tracking redirection
        private static String cleanUrl(String url) {
            return url.replace("http://lm.facebook.com/l.php?u=", "")
                    .replace("https://m.facebook.com/l.php?u=", "")
                    .replace("http://0.facebook.com/l.php?u=", "")
                    .replaceAll("&h=.*", "").replaceAll("\\?acontext=.*", "");
        }

        // url decoder, recreate all the special characters
        private static String decodeUrl(String url) {
            return url.replace("%3C", "<").replace("%3E", ">").replace("%23", "#").replace("%25", "%")
                    .replace("%7B", "{").replace("%7D", "}").replace("%7C", "|").replace("%5C", "\\")
                    .replace("%5E", "^").replace("%7E", "~").replace("%5B", "[").replace("%5D", "]")
                    .replace("%60", "`").replace("%3B", ";").replace("%2F", "/").replace("%3F", "?")
                    .replace("%3A", ":").replace("%40", "@").replace("%3D", "=").replace("%26", "&")
                    .replace("%24", "$").replace("%2B", "+").replace("%22", "\"").replace("%2C", ",")
                    .replace("%20", " ");
        }
    }

    InterstitialAd interstitial;
    private void AdMobLoad()
    {
        AdRequest adRequest = new AdRequest.Builder().build();
        interstitial = new InterstitialAd(this);
        interstitial.setAdUnitId(adMobInter);
        interstitial.setAdListener(new AdListener() {
            @Override
            public void onAdLoaded() {
                super.onAdLoaded();
            }

            @Override
            public void onAdClosed() {
                super.onAdClosed();
            }

            @Override
            public void onAdFailedToLoad(int i) {
                super.onAdFailedToLoad(i);
                            }
        });
        interstitial.loadAd(adRequest);
    }

    private void SetupMobfox() {
        Banner.setGetLocation(true);
        banner = (Banner) findViewById(R.id.banner);
        mAdView = (AdView) findViewById(R.id.adView);
        final Activity self = this;
        banner.setListener(new BannerListener() {
            @Override
            public void onBannerError(View view, Exception e) {
                //Toast.makeText(self, "error", Toast.LENGTH_SHORT).show();
                banner.setVisibility(View.GONE);
                mAdView.setVisibility(View.VISIBLE);
                AdRequest adRequest = new AdRequest.Builder().build();
                mAdView.loadAd(adRequest);
            }

            @Override
            public void onBannerLoaded(View view) {
                //Toast.makeText(self, "Loaded", Toast.LENGTH_SHORT).show();
                banner.setVisibility(View.VISIBLE);
                mAdView.setVisibility(View.GONE);
            }

            @Override
            public void onBannerClosed(View view) {
                //Toast.makeText(self, "banner closed", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onBannerFinished() {
                //Toast.makeText(self, "banner finished", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onBannerClicked(View view) {
                //Toast.makeText(self, "banner clicked", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onNoFill(View view) {
                Toast.makeText(self, "no fill", Toast.LENGTH_SHORT).show();
                banner.setVisibility(View.GONE);
                mAdView.setVisibility(View.VISIBLE);
                AdRequest adRequest = new AdRequest.Builder().build();
                mAdView.loadAd(adRequest);
            }

        });



        banner.setInventoryHash(appHash);
        banner.load();
    }

    private void SetupOnLongClickListener() {
        // OnLongClickListener for detecting long clicks on links and images
        webViewFacebook.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {

                WebView.HitTestResult result = webViewFacebook.getHitTestResult();
                int type = result.getType();
                if (type == WebView.HitTestResult.SRC_ANCHOR_TYPE || type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE
                        || type == WebView.HitTestResult.IMAGE_TYPE) {
                    Message msg = linkHandler.obtainMessage();
                    webViewFacebook.requestFocusNodeHref(msg);
                    //final String linkUrl = (String) msg.getData().get("url");
                    final String imgUrl = (String) msg.getData().get("src");
//                    if (linkUrl != null && imgUrl != null) {
//                        activity.longClickDialog(linkUrl, imgUrl);
//                    } else
// if (linkUrl != null) {
//                        activity.longClickDialog(linkUrl);
//                    } else
                    if (imgUrl != null) {
                        new AlertDialog.Builder(MainActivity.this)
                                .setTitle(getResources().getString(R.string.askDownloadPhoto))
                                //.setMessage("Do you want download this photo?")
                                .setCancelable(true)
                                .setPositiveButton(getResources().getString(R.string.yes), new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        //check permission
                                        if (Build.VERSION.SDK_INT >= 23 && ContextCompat.checkSelfPermission(MainActivity.this,
                                                android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
                                            //ask permission
                                            Toast.makeText(getApplicationContext(), getString(R.string.acceptPermissionAndRetry),
                                                    Toast.LENGTH_LONG).show();
                                            int requestResult = 0;
                                            ActivityCompat.requestPermissions(MainActivity.this,
                                                    new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, requestResult
                                            );
                                        } else {


                                            //share photo
                                            // Intent sharingIntent = new Intent(Intent.ACTION_SEND);
//												sharingIntent.setType("image/jpeg");
//												sharingIntent.putExtra(Intent.EXTRA_STREAM, imgUrl);
//												startActivity(Intent.createChooser(sharingIntent, "Share image using"));

                                            //open photo with gallery
//												Intent intent = new Intent();
//												intent.setAction(Intent.ACTION_VIEW);
//												intent.setDataAndType(Uri.parse("file://" + imgUrl), "image/*");
//												startActivity(intent);

                                            //download photo
                                            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(imgUrl));
                                            request.setTitle("SlimSocial Download");
                                            // in order for this if to run, you must use the android 3.2 to compile your app
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                                                request.allowScanningByMediaScanner();
                                                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                                            }


                                            String path=Environment.DIRECTORY_DOWNLOADS;
                                            if (savedPreferences.getBoolean("pref_useSlimSocialSubfolderToDownloadedFiles", false)) {
                                                path+= "/SlimSocial";
                                            }
                                            request.setDestinationInExternalPublicDir(path, "Photo.jpg");

                                            // get download service and enqueue file
                                            DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                                            manager.enqueue(request);
                                            Toast.makeText(getApplicationContext(), getString(R.string.downloadingPhoto),
                                                    Toast.LENGTH_LONG).show();
                                        }
                                    }
                                }).create().show();
                    }
                    return true;
                }
                return false;
            }
        });

        webViewFacebook.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                    case MotionEvent.ACTION_UP:
                        if (!v.hasFocus()) {
                            v.requestFocus();
                        }
                        break;
                }
                return false;
            }
        });
    }

    //*********************** WEBVIEW EVENTS ****************************
    @Override
    public void onPageStarted(String url, Bitmap favicon) {
        swipeRefreshLayout.setRefreshing(true);

        if (Uri.parse(url).getHost().endsWith("fbcdn.net"))
            Toast.makeText(getApplicationContext(), getResources().getString(R.string.holdImageToDownload),
                    Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onPageFinished(String url) {
        ApplyCustomCss();

        if (savedPreferences.getBoolean("pref_enableMessagesShortcut", false)) {
            webViewFacebook.loadUrl(getString(R.string.fixMessages));
        }

        swipeRefreshLayout.setRefreshing(false);

    }

    @Override
    public void onBackPressed() {
        if(webViewFacebook.canGoBack()) {
            webViewFacebook.goBack();
        }
        else {
            finish();
        }
    }

    @Override
    public void onPageError(int errorCode, String description, String failingUrl) {
        String summary = "<h1 style='text-align:center; padding-top:15%; font-size:70px;'>" +
                getString(R.string.titleNoConnection) +
                "</h1> <h3 style='text-align:center; padding-top:1%; font-style: italic;font-size:50px;'>" +
                getString(R.string.descriptionNoConnection) +
                "</h3>  <h5 style='font-size:30px; text-align:center; padding-top:80%; opacity: 0.3;'>" +
                getString(R.string.awards) +
                "</h5>";
        webViewFacebook.loadData(summary, "text/html; charset=utf-8", "utf-8");//load a custom html page
        noConnectionError = true;//to allow to return at the last visited page
    }

    @Override
    public void onExternalPageRequest(String url) {//if the link doesn't contain 'facebook.com', open it using the browser
        if (Uri.parse(url).getHost().endsWith("slimsocial.leo")) {
            //he clicked on messages
            startActivity(new Intent(this, MessagesActivity.class));
        } else {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (ActivityNotFoundException e) {//this prevents the crash
                Log.e("shouldOverrideUrlLoad", "" + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onDownloadRequested(String url, String suggestedFilename, String mimeType, long contentLength, String contentDisposition, String userAgent) {

    }

    private void ShareLinkHandler() {
        /** get a subject and text and check if this is a link trying to be shared */
        String sharedSubject = getIntent().getStringExtra(Intent.EXTRA_SUBJECT);
        String sharedUrl = getIntent().getStringExtra(Intent.EXTRA_TEXT);
        Log.d("sharedUrl", "ShareLinkHandler() - sharedUrl: " + sharedUrl);

        // if we have a valid URL that was shared by us, open the sharer
        if (sharedUrl != null) {
            if (!sharedUrl.equals("")) {
                // check if the URL being shared is a proper web URL
                if (!sharedUrl.startsWith("http://") || !sharedUrl.startsWith("https://")) {
                    // if it's not, let's see if it includes an URL in it (prefixed with a message)
                    int startUrlIndex = sharedUrl.indexOf("http:");
                    if (startUrlIndex > 0) {
                        // seems like it's prefixed with a message, let's trim the start and get the URL only
                        sharedUrl = sharedUrl.substring(startUrlIndex);
                    }
                }
                // final step, set the proper Sharer...
                urlSharer = String.format("https://m.facebook.com/sharer.php?u=%s&t=%s", sharedUrl, sharedSubject);
                // ... and parse it just in case
                urlSharer = Uri.parse(urlSharer).toString();
                isSharer = true;
            }
        }
    }

      /*//to check if there is the key for future use
    //I 'll never add premium features but I would acknowledge who has buyed the app
    protected boolean isProInstalled(Context context) {
        // the packagename of the 'key' app
        String proPackage = "it.rignanese.leo.donationkey1";

        // get the package manager
        final PackageManager pm = context.getPackageManager();

        // get a list of installed packages
        List<PackageInfo> list = pm.getInstalledPackages(PackageManager.GET_DISABLED_COMPONENTS);

        // let's iterate through the list
        Iterator<PackageInfo> i = list.iterator();
        while (i.hasNext()) {
            PackageInfo p = i.next();
            // check if proPackage is in the list AND whether that package is signed
            //  with the same signature as THIS package
            if ((p.packageName.equals(proPackage)) &&
                    (pm.checkSignatures(context.getPackageName(), p.packageName) == PackageManager.SIGNATURE_MATCH))
                return true;
        }
        return false;
    }*/
}

