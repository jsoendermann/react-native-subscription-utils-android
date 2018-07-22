package com.primlo.subscriptionutils;

import java.util.List;
import java.util.ArrayList;
import android.util.Log;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClient.BillingResponse;
import com.android.billingclient.api.BillingClient.FeatureType;
import com.android.billingclient.api.BillingClient.SkuType;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.ConsumeResponseListener;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.Purchase.PurchasesResult;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.SkuDetails;
import com.android.billingclient.api.SkuDetailsParams;
import com.android.billingclient.api.SkuDetailsResponseListener;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.ReactApplication;
import com.facebook.react.ReactNativeHost;
import com.facebook.react.devsupport.interfaces.DevSupportManager;
import com.facebook.react.devsupport.interfaces.DevOptionHandler;
import com.facebook.react.modules.core.RCTNativeAppEventEmitter;

public class SubscriptionUtilsModule extends ReactContextBaseJavaModule implements PurchasesUpdatedListener {
  private static final String TAG = "SubscriptionUtils";

  private static final String ERR_CONNECTION_ERROR = "ERR_CONNECTION_ERROR";
  private static final String ERR_COULD_NOT_LOAD_PRODUCTS = "ERR_COULD_NOT_LOAD_PRODUCTS";
  private static final String ERR_COULD_NOT_LAUNCH_BILLING_FLOW = "ERR_COULD_NOT_LAUNCH_BILLING_FLOW";
  private static final String ERR_COULD_NOT_QUERY_PURCHASES = "ERR_COULD_NOT_QUERY_PURCHASES";

  private static final String EVENT_CONNECTION_LOST = "com.primlo.subscripiton-utils.android.connection-lost";
  private static final String EVENT_PURCHASE_UPDATED = "com.primlo.subscripiton-utils.android.purchase-updated";

  private BillingClient mBillingClient;

  public SubscriptionUtilsModule(ReactApplicationContext reactContext) {
    super(reactContext);
  }

  @Override
  public String getName() {
    return "SubscriptionUtils";
  }

  private void sendEvent(String eventName, WritableMap params) {
    getReactApplicationContext().getJSModule(RCTNativeAppEventEmitter.class).emit(eventName, params);
  }

  @ReactMethod
  public void connect(final Promise promise) {
    this.mBillingClient = BillingClient.newBuilder(getCurrentActivity()).setListener(this).build();
    this.mBillingClient.startConnection(new BillingClientStateListener() {
      @Override
      public void onBillingSetupFinished(@BillingResponse int billingResponseCode) {
        if (billingResponseCode == BillingResponse.OK) {
          promise.resolve(null);
        } else {
          promise.reject(ERR_CONNECTION_ERROR, billingResponseToString(billingResponseCode));
        }
      }

      @Override
      public void onBillingServiceDisconnected() {
        sendEvent(EVENT_CONNECTION_LOST, null);
      }
    });
  }

  @ReactMethod
  public void loadProducts(ReadableArray jsSkuList, final Promise promise) {
    List skuList = new ArrayList<>();
    for (int i = 0; i < jsSkuList.size(); i++) {
      skuList.add(jsSkuList.getString(i));
    }
    SkuDetailsParams.Builder params = SkuDetailsParams.newBuilder();
    params.setSkusList(skuList).setType(SkuType.SUBS);
    mBillingClient.querySkuDetailsAsync(params.build(), new SkuDetailsResponseListener() {
      @Override
      public void onSkuDetailsResponse(int responseCode, List<SkuDetails> skuDetailsList) {
        if (responseCode == BillingResponse.OK) {
          WritableArray skuDetails = Arguments.createArray();
          for (SkuDetails d : skuDetailsList) {
            WritableMap map = Arguments.createMap();
            map.putString("description", d.getDescription());
            map.putString("freeTrialPeriod", d.getFreeTrialPeriod());
            map.putString("introductoryPrice", d.getIntroductoryPrice());
            map.putString("introductoryPriceAmountMicros", d.getIntroductoryPriceAmountMicros());
            map.putString("introductoryPriceCycles", d.getIntroductoryPriceCycles());
            map.putString("introductoryPricePeriod", d.getIntroductoryPricePeriod());
            map.putString("price", d.getPrice());
            map.putString("priceAmountMicros", Long.toString(d.getPriceAmountMicros()));
            map.putString("priceCurrencyCode", d.getPriceCurrencyCode());
            map.putString("sku", d.getSku());
            map.putString("subscriptionPeriod", d.getSubscriptionPeriod());
            map.putString("title", d.getTitle());
            map.putString("type", d.getType());
            skuDetails.pushMap(map);
          }
          promise.resolve(skuDetails);
        } else {
          promise.reject(ERR_COULD_NOT_LOAD_PRODUCTS, billingResponseToString(responseCode));
        }
      }
    });
  }

  @ReactMethod
  public void launchBillingFlow(final String skuId, final Promise promise) {
    Runnable purchaseFlowRequest = new Runnable() {
      @Override
      public void run() {
        // Log.d(TAG, "Launching billing flow.");

        BillingFlowParams flowParams = BillingFlowParams.newBuilder().setSku(skuId).setType(SkuType.SUBS).build();
        int responseCode = mBillingClient.launchBillingFlow(getCurrentActivity(), flowParams);

        if (responseCode == BillingResponse.OK) {
          promise.resolve(null);
        } else {
          promise.reject(ERR_COULD_NOT_LAUNCH_BILLING_FLOW, billingResponseToString(responseCode));
        }
      }
    };

    purchaseFlowRequest.run();
  }

  @Override
  public void onPurchasesUpdated(@BillingResponse int responseCode, List<Purchase> purchases) {
    WritableMap purchaseData = purchaseDataToMap(responseCode, purchases);
    sendEvent(EVENT_PURCHASE_UPDATED, purchaseData);
  }

  @ReactMethod
  public void queryPurchases(Promise promise) {
    PurchasesResult purchasesResult = mBillingClient.queryPurchases(SkuType.SUBS);
    int responseCode = purchasesResult.getResponseCode();
    List<Purchase> purchases = purchasesResult.getPurchasesList();

    if (responseCode == BillingResponse.OK) {
      WritableMap purchaseData = purchaseDataToMap(responseCode, purchases);
      promise.resolve(purchaseData);
    } else {
      promise.reject(ERR_COULD_NOT_QUERY_PURCHASES, billingResponseToString(responseCode));
    }
  }

  private static WritableMap purchaseDataToMap(@BillingResponse int responseCode, List<Purchase> purchases) {
    WritableMap ret = Arguments.createMap();
    ret.putString("responseCode", billingResponseToString(responseCode));

    if (purchases != null) {
      ret.putNull("purchases");
    } else {
      WritableArray purchasesArray = Arguments.createArray();

      for (Purchase purchase : purchases) {
        WritableMap map = Arguments.createMap();
        map.putString("orderId", purchase.getOrderId());
        map.putString("originalJson", purchase.getOriginalJson());
        map.putString("packageName", purchase.getPackageName());
        map.putString("purchaseTime", Long.toString(purchase.getPurchaseTime()));
        map.putString("purchaseToken", purchase.getPurchaseToken());
        map.putString("signature", purchase.getSignature());
        map.putString("sku", purchase.getSku());
        map.putBoolean("isAutoRenewing", purchase.isAutoRenewing());
        purchasesArray.pushMap(map);
      }

      ret.putArray("purchases", purchasesArray);
    }

    return ret;
  }

  private static String billingResponseToString(@BillingResponse int responseCode) {
    switch (responseCode) {
    case BillingResponse.BILLING_UNAVAILABLE:
      return "BILLING_UNAVAILABLE";
    case BillingResponse.DEVELOPER_ERROR:
      return "DEVELOPER_ERROR";
    case BillingResponse.ERROR:
      return "ERROR";
    case BillingResponse.FEATURE_NOT_SUPPORTED:
      return "FEATURE_NOT_SUPPORTED";
    case BillingResponse.ITEM_ALREADY_OWNED:
      return "ITEM_ALREADY_OWNED";
    case BillingResponse.ITEM_NOT_OWNED:
      return "ITEM_NOT_OWNED";
    case BillingResponse.ITEM_UNAVAILABLE:
      return "ITEM_UNAVAILABLE";
    case BillingResponse.OK:
      return "OK";
    case BillingResponse.SERVICE_DISCONNECTED:
      return "SERVICE_DISCONNECTED";
    case BillingResponse.SERVICE_UNAVAILABLE:
      return "SERVICE_UNAVAILABLE";
    case BillingResponse.USER_CANCELED:
      return "USER_CANCELED";
    default:
      return "UNKNOWN_BILLING_RESPONSE";
    }
  }
}