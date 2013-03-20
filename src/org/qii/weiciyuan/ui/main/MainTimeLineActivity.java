package org.qii.weiciyuan.ui.main;

import android.app.ActionBar;
import android.app.FragmentTransaction;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import com.slidingmenu.lib.SlidingMenu;
import org.qii.weiciyuan.R;
import org.qii.weiciyuan.bean.AccountBean;
import org.qii.weiciyuan.bean.UnreadBean;
import org.qii.weiciyuan.bean.UserBean;
import org.qii.weiciyuan.dao.unread.UnreadDao;
import org.qii.weiciyuan.othercomponent.ClearCacheTask;
import org.qii.weiciyuan.othercomponent.unreadnotification.UnreadMsgReceiver;
import org.qii.weiciyuan.support.error.WeiboException;
import org.qii.weiciyuan.support.lib.MyAsyncTask;
import org.qii.weiciyuan.support.settinghelper.SettingUtility;
import org.qii.weiciyuan.support.utils.AppLogger;
import org.qii.weiciyuan.support.utils.GlobalContext;
import org.qii.weiciyuan.support.utils.Utility;
import org.qii.weiciyuan.ui.basefragment.AbstractTimeLineFragment;
import org.qii.weiciyuan.ui.interfaces.IAccountInfo;
import org.qii.weiciyuan.ui.interfaces.IUserInfo;
import org.qii.weiciyuan.ui.maintimeline.*;
import org.qii.weiciyuan.ui.send.WriteWeiboActivity;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * User: Jiang Qi
 * Date: 12-7-27
 */
public class MainTimeLineActivity extends MainTimeLineParentActivity implements IUserInfo,
        IAccountInfo {

    private AccountBean accountBean;

    private GetUnreadCountTask getUnreadCountTask;

    private NewMsgBroadcastReceiver newMsgBroadcastReceiver;

    private ScheduledExecutorService newMsgScheduledExecutorService;

    private MusicReceiver musicReceiver;

    public String getToken() {
        return accountBean.getAccess_token();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable("account", accountBean);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            accountBean = (AccountBean) savedInstanceState.getSerializable("account");
        } else {
            Intent intent = getIntent();
            accountBean = (AccountBean) intent.getSerializableExtra("account");
        }

        if (accountBean == null)
            accountBean = GlobalContext.getInstance().getAccountBean();

        GlobalContext.getInstance().setAccountBean(accountBean);
        SettingUtility.setDefaultAccountId(accountBean.getUid());

        buildPhoneInterface(savedInstanceState);

        Executors.newSingleThreadScheduledExecutor().schedule(new ClearCacheTask(), 8, TimeUnit.SECONDS);

        startListenMusicPlaying();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (musicReceiver != null)
            unregisterReceiver(musicReceiver);
    }

    private void startListenMusicPlaying() {
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                IntentFilter musicFilter = new IntentFilter();
                musicFilter.addAction("com.android.music.metachanged");
                musicFilter.addAction("com.android.music.playstatechanged");
                musicFilter.addAction("com.android.music.playbackcomplete");
                musicFilter.addAction("com.android.music.queuechanged");

                musicFilter.addAction("com.htc.music.metachanged");
                musicFilter.addAction("fm.last.android.metachanged");
                musicFilter.addAction("com.sec.android.app.music.metachanged");
                musicFilter.addAction("com.nullsoft.winamp.metachanged");
                musicFilter.addAction("com.amazon.mp3.metachanged");
                musicFilter.addAction("com.miui.player.metachanged");
                musicFilter.addAction("com.real.IMP.metachanged");
                musicFilter.addAction("com.sonyericsson.music.metachanged");
                musicFilter.addAction("com.rdio.android.metachanged");
                musicFilter.addAction("com.samsung.sec.android.MusicPlayer.metachanged");
                musicFilter.addAction("com.andrew.apollo.metachanged");
                musicReceiver = new MusicReceiver();
                registerReceiver(musicReceiver, musicFilter);
            }
        }, 3000);
    }

    private class MusicReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String artist = intent.getStringExtra("artist");
            String album = intent.getStringExtra("album");
            String track = intent.getStringExtra("track");
            if (!TextUtils.isEmpty(track)) {
                MusicInfo musicInfo = new MusicInfo();
                musicInfo.setArtist(artist);
                musicInfo.setAlbum(album);
                musicInfo.setTrack(track);
                AppLogger.d("Music" + artist + ":" + album + ":" + track);
                GlobalContext.getInstance().updateMusicInfo(musicInfo);
            }
        }
    }

    ;

    public static class MusicInfo {
        String artist;
        String album;
        String track;

        public void setArtist(String artist) {
            this.artist = artist;
        }

        public void setAlbum(String album) {
            this.album = album;
        }

        public void setTrack(String track) {
            this.track = track;
        }

        @Override
        public String toString() {
            if (!TextUtils.isEmpty(artist))
                return "Now Playing:" + artist + ":" + track;
            else
                return "Now Playing:" + track;
        }

        public boolean isEmpty() {
            return TextUtils.isEmpty(track);
        }
    }

    private void getUnreadCount() {
        if (Utility.isTaskStopped(getUnreadCountTask)) {
            getUnreadCountTask = new GetUnreadCountTask();
            getUnreadCountTask.executeOnExecutor(MyAsyncTask.THREAD_POOL_EXECUTOR);
        }
    }


    private void buildPhoneInterface(Bundle savedInstanceState) {
        ActionBar actionBar = getActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        getActionBar().setTitle(GlobalContext.getInstance().getCurrentAccountName());
        getWindow().setBackgroundDrawable(null);
        setContentView(R.layout.menu_right);
        setBehindContentView(R.layout.menu_frame);

        View title = getLayoutInflater().inflate(R.layout.maintimelineactivity_title_layout, null);

        View clickToTop = title.findViewById(R.id.tv_click_to_top);
        clickToTop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                scrollCurrentListViewToTop();
            }
        });
        View write = title.findViewById(R.id.btn_write);
        write.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainTimeLineActivity.this, WriteWeiboActivity.class);
                intent.putExtra("token", GlobalContext.getInstance().getSpecialToken());
                intent.putExtra("account", GlobalContext.getInstance().getAccountBean());
                startActivity(intent);
            }
        });
        ActionBar.LayoutParams layoutParams = new ActionBar.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.RIGHT);
        actionBar.setCustomView(title, layoutParams);
        actionBar.setDisplayShowCustomEnabled(true);

        if (savedInstanceState == null) {
            FragmentTransaction fragmentTransaction = getFragmentManager().beginTransaction();
            fragmentTransaction.replace(R.id.menu_frame, getMenuFragment(), LeftMenuFragment.class.getName());
            fragmentTransaction.replace(R.id.menu_right_fl, getFriendsTimeLineFragment(), FriendsTimeLineFragment.class.getName());
            fragmentTransaction.commit();
        } else {
            FragmentTransaction fragmentTransaction = getFragmentManager().beginTransaction();
            fragmentTransaction.remove(getMentionsTimeLineFragment());
            fragmentTransaction.remove(getMentionsCommentTimeLineFragment());
            fragmentTransaction.remove(getCommentsTimeLineFragment());
            fragmentTransaction.remove(getCommentsByMeTimeLineFragment());
            fragmentTransaction.commit();
        }

        SlidingMenu slidingMenu = getSlidingMenu();
        slidingMenu.setMode(SlidingMenu.LEFT);
        slidingMenu.setTouchModeAbove(SlidingMenu.TOUCHMODE_MARGIN);
        slidingMenu.setShadowWidthRes(R.dimen.shadow_width);
        slidingMenu.setShadowDrawable(R.drawable.shadow);
        slidingMenu.setBehindOffsetRes(R.dimen.slidingmenu_offset);
        slidingMenu.setFadeDegree(0.35f);


    }

    private AbstractTimeLineFragment currentFragment;

    private void scrollCurrentListViewToTop() {
        ListView listView;
        if (currentFragment == null) {
            listView = getFriendsTimeLineFragment().getListView();
        } else {
            listView = currentFragment.getListView();
        }
        listView.smoothScrollToPositionFromTop(0, 0);

    }

    public void setCurrentFragment(AbstractTimeLineFragment fragment) {
        this.currentFragment = fragment;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        AccountBean newAccountBean = (AccountBean) intent.getSerializableExtra("account");
        if (newAccountBean == null) {
            return;
        }

        if (newAccountBean.getUid().equals(accountBean.getUid())) {
            accountBean = newAccountBean;
            GlobalContext.getInstance().setAccountBean(accountBean);
        } else {
            overridePendingTransition(0, 0);
            finish();
            overridePendingTransition(0, 0);
            startActivity(intent);
            overridePendingTransition(0, 0);
        }

    }


    @Override
    public void onBackPressed() {
        super.onBackPressed();
        GlobalContext.getInstance().startedApp = false;
        GlobalContext.getInstance().getAvatarCache().evictAll();
        finish();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent intent;
        switch (item.getItemId()) {
            case android.R.id.home:
                getSlidingMenu().showMenu();
                return true;

        }

        return super.onOptionsItemSelected(item);
    }


    @Override
    public UserBean getUser() {
        return accountBean.getInfo();

    }


    @Override
    public AccountBean getAccount() {
        return accountBean;
    }


    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter(UnreadMsgReceiver.ACTION);
        filter.setPriority(1);
        newMsgBroadcastReceiver = new NewMsgBroadcastReceiver();
        registerReceiver(newMsgBroadcastReceiver, filter);

        newMsgScheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
        newMsgScheduledExecutorService.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                getUnreadCount();
            }
        }, 10, 50, TimeUnit.SECONDS);

    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(newMsgBroadcastReceiver);
        newMsgScheduledExecutorService.shutdownNow();
        if (getUnreadCountTask != null)
            getUnreadCountTask.cancel(true);
    }


    public LeftMenuFragment getMenuFragment() {
        LeftMenuFragment fragment = ((LeftMenuFragment) getFragmentManager().findFragmentByTag(
                LeftMenuFragment.class.getName()));
        if (fragment == null) {
            fragment = new LeftMenuFragment();
        }
        return fragment;
    }

    public MentionsCommentTimeLineFragment getMentionsCommentTimeLineFragment() {
        MentionsCommentTimeLineFragment fragment = ((MentionsCommentTimeLineFragment) getFragmentManager().findFragmentByTag(
                MentionsCommentTimeLineFragment.class.getName()));
        if (fragment == null) {
            fragment = new MentionsCommentTimeLineFragment(getAccount(), getUser(), getToken());
        }
        return fragment;
    }


    public FriendsTimeLineFragment getFriendsTimeLineFragment() {
        FriendsTimeLineFragment fragment = ((FriendsTimeLineFragment) getFragmentManager().findFragmentByTag(
                FriendsTimeLineFragment.class.getName()));
        if (fragment == null)
            fragment = new FriendsTimeLineFragment(getAccount(), getUser(), getToken());

        return fragment;
    }

    public MentionsWeiboTimeLineFragment getMentionsTimeLineFragment() {
        MentionsWeiboTimeLineFragment fragment = ((MentionsWeiboTimeLineFragment) getFragmentManager().findFragmentByTag(
                MentionsWeiboTimeLineFragment.class.getName()));
        if (fragment == null)
            fragment = new MentionsWeiboTimeLineFragment(getAccount(), getUser(), getToken());

        return fragment;
    }

    public CommentsToMeTimeLineFragment getCommentsTimeLineFragment() {
        CommentsToMeTimeLineFragment fragment = ((CommentsToMeTimeLineFragment) getFragmentManager().findFragmentByTag(
                CommentsToMeTimeLineFragment.class.getName()));
        if (fragment == null)
            fragment = new CommentsToMeTimeLineFragment(getAccount(), getUser(), getToken());

        return fragment;
    }

    public CommentsByMeTimeLineFragment getCommentsByMeTimeLineFragment() {
        CommentsByMeTimeLineFragment fragment = ((CommentsByMeTimeLineFragment) getFragmentManager().findFragmentByTag(
                CommentsByMeTimeLineFragment.class.getName()));
        if (fragment == null)
            fragment = new CommentsByMeTimeLineFragment(getAccount(), getUser(), getToken());

        return fragment;
    }


    private class GetUnreadCountTask extends MyAsyncTask<Void, Void, UnreadBean> {

        @Override
        protected UnreadBean doInBackground(Void... params) {
            UnreadDao unreadDao = new UnreadDao(getToken(), accountBean.getUid());
            try {
                return unreadDao.getCount();
            } catch (WeiboException e) {
                AppLogger.e(e.getError());
            }
            return null;
        }

        @Override
        protected void onPostExecute(UnreadBean unreadBean) {
            super.onPostExecute(unreadBean);
            if (unreadBean != null) {
                buildUnreadTabTxt(unreadBean);

            }
        }
    }

    private class NewMsgBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {


            AccountBean newMsgAccountBean = (AccountBean) intent.getSerializableExtra("account");
            if (newMsgAccountBean.getUid().equals(MainTimeLineActivity.this.accountBean.getUid())) {
                abortBroadcast();
                UnreadBean unreadBean = (UnreadBean) intent.getSerializableExtra("unread");
                buildUnreadTabTxt(unreadBean);

            }

        }
    }

    private void buildUnreadTabTxt(UnreadBean unreadBean) {

    }


}