package com.mitnick.rxjava.http;

import android.app.Activity;
import android.util.Log;
import org.greenrobot.eventbus.EventBus;

import com.google.gson.Gson;
import com.mitnick.rxjava.http.bean.Profile;
import com.mitnick.rxjava.http.bean.RefreshRequest;
import com.mitnick.rxjava.http.bean.Token;
import com.mitnick.rxjava.util.L;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import rx.Observable;
import rx.Observer;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.schedulers.Schedulers;
import rx.subscriptions.CompositeSubscription;


/**
 * Created by mitnick.cheng on 2016/7/24.
 */

public class HttpImpl {
    private final static String TAG = "HttpImpl";

    static volatile HttpImpl sInstance;
    static volatile Http mApiClient;


    private HttpImpl() {
    }

    public Http getApiClient() {
        if (mApiClient == null) {
            synchronized (this) {
                mApiClient = HttpFactory.createRetrofit2RxJavaService(Http.class);
            }
        }
        return mApiClient;
    }

    //获取唯一单列
    public static HttpImpl getInstance() {
        if (sInstance == null) {
            synchronized (HttpImpl.class) {
                sInstance = new HttpImpl();
            }
        }
        return sInstance;
    }

    private final void postEvent(Object object) {
        EventBus.getDefault().post(object);
    }

    public  void login(String auth) {
        Subscription subscription =  getApiClient().login(auth)
                        .doOnNext(new Action1<Token>(){//该方法执行请求成功后的耗时操作，比如数据库读写
                            @Override
                            public void call(Token token) {
                                L.i(TAG,"doOnNext() " + new Gson().toJson(token));
                            }
                        })
//                      .debounce(400, TimeUnit.MILLISECONDS)//限制400毫秒的频繁http操作
                        .subscribeOn(Schedulers.newThread())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(new rx.Observer<Token>() {
                            @Override
                            public void onCompleted() {
                                Log.i(TAG, "onCompleted");
                            }

                            @Override
                            public void onError(Throwable throwable) {
                                L.e(TAG , "login 请求失败！" + throwable.toString());
                                postEvent(new FailedEvent(MessageType.LOGIN));
                            }

                            @Override
                            public void onNext(Token token) {
                                L.i(TAG,"onNext()");
                                postEvent(token);
                            }
                        });
        RxJavaManager.getRxInstance().getCompositeSubscription().add(subscription);

    }

    public void getProfiles(String accessToken) {
        Call<Profile> call = getApiClient().getProfiles(accessToken);
        call.enqueue(new Callback<Profile>() {
            @Override
            public void onResponse(Call<Profile> call, Response<Profile> response) {
                if (response.isSuccessful()) {
                    postEvent(response.body());
                } else {
                    L.e(TAG , "getProfiles 请求失败！" +  response.code());
                    postEvent(new FailedEvent(MessageType.PROFILE));
                }
            }

            @Override
            public void onFailure(Call<Profile> call, Throwable throwable) {
                L.e(TAG , "getProfiles 请求失败！" + throwable.toString());
                postEvent(new FailedEvent(MessageType.PROFILE));
            }
        });
    }

    public void getProfile(String accessToken) {
        Subscription subscription = getApiClient().getProfile(accessToken)
        //               .debounce(400, TimeUnit.MILLISECONDS)//限制400毫秒的频繁http操作
                        .subscribeOn(Schedulers.newThread())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(new rx.Observer<Profile>() {
                            @Override
                            public void onCompleted() {
                                L.i("onCompleted");
                            }

                            @Override
                            public void onError(Throwable throwable) {
                                L.e(TAG , "getProfile 请求失败！" + throwable.toString());
                                postEvent(new FailedEvent(MessageType.PROFILE));
                            }

                            @Override
                            public void onNext(Profile profile) {
                                postEvent(profile);
                            }
                        });
        RxJavaManager.getRxInstance().getCompositeSubscription().add(subscription);
    }

    public void refresh(String refreshToken) {
        Call<Token> call = getApiClient().refresh(new RefreshRequest(refreshToken));
        call.enqueue(new Callback<Token>() {
            @Override
            public void onResponse(Call<Token> call, Response<Token> response) {
                if (response.isSuccessful()) {
                    postEvent(response.body());
                } else {
                    L.e(TAG,"refresh 请求失败！" +  response.code());
                    postEvent(new FailedEvent(MessageType.REFRESH));
                }
            }

            @Override
            public void onFailure(Call<Token> call, Throwable throwable) {
                L.e(TAG,"refresh 请求失败！" + throwable.toString());
                postEvent(new FailedEvent(MessageType.REFRESH));
            }
        });
    }

    //先登录，然后马上请求信息，连续请求2次网络请求
    public void loginAndGetProfile(String auth){
        Subscription subscription =   getApiClient().login(auth)
                .flatMap(new Func1<Token, Observable<Profile>>() {
                    @Override
                    public Observable<Profile> call(Token token) {
                        L.e(TAG,"loginAndGetProfile flatMap 请求成功！ " + new Gson().toJson(token));
                        return getApiClient().getProfile(token.getAccess_token());
                    }
                })
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<Profile>() {
                    @Override
                    public void onCompleted() {
                        L.e(TAG , "onCompleted！");
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        L.e(TAG,"loginAndGetProfile 请求失败！" + throwable.toString());
                        postEvent(new FailedEvent(MessageType.PROFILE));
                    }

                    @Override
                    public void onNext(Profile profile) {
                        L.e(TAG,"loginAndGetProfile 请求成功！" + new Gson().toJson(profile));
                        postEvent(profile);
                    }
                });
        RxJavaManager.getRxInstance().getCompositeSubscription().add(subscription);
    }
}
