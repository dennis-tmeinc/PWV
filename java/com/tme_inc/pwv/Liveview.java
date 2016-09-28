package com.tme_inc.pwv;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import java.util.Scanner;

public class Liveview extends PwViewActivity {
    private boolean m_covertmode = false ;
    private boolean m_loading ;

    private int [] m_RecId = { R.id.rec1, R.id.rec2, R.id.rec3, R.id.rec4, R.id.rec5, R.id.rec6, R.id.rec7, R.id.rec8 };
    private int [] m_RecImage = {
            R.drawable.rec1,
            R.drawable.rec2,
            R.drawable.rec3,
            R.drawable.rec4,
            R.drawable.rec5,
            R.drawable.rec6,
            R.drawable.rec7,
            R.drawable.rec8
    };
    private int [] m_NoRecImage = {
            R.drawable.norec1,
            R.drawable.norec2,
            R.drawable.norec3,
            R.drawable.norec4,
            R.drawable.norec5,
            R.drawable.norec6,
            R.drawable.norec7,
            R.drawable.norec8
    };

    private long       mStatusTime ;

    private boolean m_diskwaringflash = false ;   // to flash message

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_liveview);

        // setup player screen
        setupScreen();

        ImageButton button;
        button = (ImageButton)findViewById(R.id.button_tag) ;
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //TagEventDialog tagDialog = new TagEventDialog();
                //tagDialog.show(getFragmentManager(), "tagTagEvent");
                savePref();
                Intent intent = new Intent(getBaseContext(), TagEventActivity.class);
                //Intent intent = new Intent(getBaseContext(), TagEvent.class);
                if( mplayer!=null ) {
                    intent.putExtra("DvrTime", mplayer.getVideoTimestamp());
                }
                startActivity(intent);
            }
        });

        button = (ImageButton)findViewById(R.id.button_covert) ;
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                m_covertmode = true ;

                mPwProtocol.SetCovertMode(true);

                // Show Covert Screen
                Intent intent = new Intent(getBaseContext(), CovertScreenActivity.class);
                startActivity(intent);
            }
        });

        button= (ImageButton)findViewById(R.id.button_officer) ;
        button.setOnClickListener(new View.OnClickListener() {
                                      @Override
                                      public void onClick(View v) {
                                          OfficerIdDialog officerIdDialog = new OfficerIdDialog();
                                          officerIdDialog.show(getFragmentManager(), "tagOfficerId");
                                      }
                                  }
        );

        button= (ImageButton)findViewById(R.id.button_cam1) ;
        button.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mPwProtocol.SendPWKey(PWProtocol.PW_VK_C1_DOWN, null);
                        ((ImageButton)v).setImageResource(R.drawable.pw_cam1_trans);
                        mStatusTime=0;
                    }
                }
        );
        button= (ImageButton)findViewById(R.id.button_cam2) ;
        button.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mPwProtocol.SendPWKey(PWProtocol.PW_VK_C2_DOWN, null);
                        ((ImageButton)v).setImageResource(R.drawable.pw_cam2_trans);
                        mStatusTime=0;
                    }
                }
        );
        button= (ImageButton)findViewById(R.id.button_tm) ;
        button.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mPwProtocol.SendPWKey(PWProtocol.PW_VK_TM_DOWN, null);
                        mStatusTime=0;
                    }
                }
        );
        button= (ImageButton)findViewById(R.id.button_lp) ;
        button.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        ImageButton bt = (ImageButton)v ;
                        boolean selected = bt.isSelected();
                        bt.setSelected(!selected);
                        selected = bt.isSelected();

                        if (selected) {
                            mPwProtocol.SendPWKey(PWProtocol.PW_VK_LP_DOWN, null);
                            m_UIhandler.sendEmptyMessageDelayed(PWMessage.MSG_PW_LPOFF, 5000);
                        } else {
                            m_UIhandler.removeMessages(PWMessage.MSG_PW_LPOFF );
                            mPwProtocol.SendPWKey(PWProtocol.PW_VK_LP_UP, null);
                        }

                        mStatusTime=0;
                    }
                }
        );

        button= (ImageButton)findViewById(R.id.btPlayMode) ;
        button.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent intent = new Intent(getBaseContext(), Playback.class);
                        startActivity(intent);
                        finish();
                    }
                }
        );

        m_UIhandler = new Handler(){
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);

                if(msg.what == PWMessage.MSG_UI_HIDE ) {
                    hideUI();
                }
                else if( msg.what == PWMessage.MSG_PW_LPOFF ) {
                    ImageButton button= (ImageButton)findViewById(R.id.button_lp) ;
                    boolean selected = button.isSelected();
                    if( selected ) {
                        button.setSelected(false);
                        mPwProtocol.SendPWKey(PWProtocol.PW_VK_LP_UP, null);
                    }
                }
            }
        };

    }

    @Override
    protected void onResume() {
        super.onResume();

        // set Police ID
        SharedPreferences prefs = getSharedPreferences("pwv", 0);
        boolean useLogin = prefs.getBoolean("login", false);
        String officerId = prefs.getString("officerId", "");
        if( !useLogin && officerId.length()>0 ) {
            mPwProtocol.SetOfficerId(officerId,null);
        }

        // save live screen active flag
        SharedPreferences.Editor prefEdit = prefs.edit();
        prefEdit.putBoolean("live", true) ;           // stop using remote login on launch
        prefEdit.commit() ;

        if( m_covertmode ) {
            m_covertmode = false ;
            mPwProtocol.SetCovertMode(false);
        }

    }

    @Override
    protected void onPause() {
        super.onPause();

        savePref();
    }

    @Override
    protected void onAnimate( long totalTime, long deltaTime) {
        super.onAnimate(totalTime,deltaTime);

        if( mstream == null ) {
            mstream = new PWLiveStream(m_channel);
            mstream.start();
            m_loading = true ;
            findViewById(R.id.loadingBar).setVisibility(View.VISIBLE);
            ((TextView)findViewById(R.id.loadingText)).setText("Loading...");
            findViewById(R.id.loadingText).setVisibility(View.VISIBLE);

            // clear OSD ;
            for(int idx=0; idx<m_maxosd ; idx++ ) {
                if (m_osd[idx] != null) {
                    m_osd[idx].setText("");
                    m_osd[idx].setVisibility(View.INVISIBLE);
                }
            }
            return ;
        }
        else if( mplayer==null ) {
            String cn = mstream.getChannelName() ;
            if( cn!=null )
                ((TextView)findViewById(R.id.loadingText)).setText( cn );
            if( mstream.videoAvailable() ) {
                m_totalChannel = mstream.totalChannels ;
                //startActivity(new Intent(getBaseContext(), Playback.class));
                TextureView textureView = (TextureView)findViewById(R.id.liveScreen);
                if( textureView!=null && textureView.isAvailable()) {
                    SurfaceTexture surface = textureView.getSurfaceTexture();
                    if (surface != null && mstream.getResolution()>=0 ) {
                        //mplayer = PWPlayer.CreatePlayer(this, new Surface(surface), mstream.mRes );
                        mplayer = new PWPlayer( this, new Surface(surface), true );
                        if( mstream.video_width > 50 && mstream.video_height>50 ) {
                            mplayer.setFormat( mstream.video_width, mstream.video_height ) ;
                        }
                        else {
                            mplayer.setFormat( mstream.getResolution() ) ;
                        }
                        mplayer.setAudioFormat( mstream.audio_codec, mstream.audio_samplerate ) ;
                        mplayer.start();
                    }
                }
            }
            else if( !mstream.isRunning() ) {
                goNextChannel();
            }

            return ;
        }

        if( mplayer.inputReady() && mstream.videoAvailable() ) {
            mplayer.writeInput(mstream.getVideoFrame());
        }

        if( mstream.audioAvailable() ) {
            mplayer.writeAudio(mstream.getAudioFrame()) ;
        }

        // Render output buffer if available
        if( mplayer.outputReady() ) {
            long ats = mplayer.getAudioTimestamp();
            long vts = mplayer.getVideoTimestamp();
            if( vts<=ats || ats==0 ) {
                if( mplayer.popOutput(true) ) {
                    if (m_loading) {
                        findViewById(R.id.loadingBar).setVisibility(View.INVISIBLE);
                        findViewById(R.id.loadingText).setVisibility(View.INVISIBLE);
                        m_loading = false;
                    }
                }
            }
        }

        MediaFrame txtFrame = null ;
        while( mstream.textAvailable()){
            txtFrame = mstream.getTextFrame() ;
        }
        displayOSD( txtFrame );

        // PW status update
        mStatusTime += deltaTime ;
        if( mStatusTime>1000 && !mPwProtocol.isBusy() ) {       // every second
            mStatusTime = 0;
            mPwProtocol.GetPWStatus(pwStatusListener);
        }
        return;
    }

    int xappmod = 0 ;
    PWProtocol.PWListener pwStatusListener = new PWProtocol.PWListener() {
        @Override
        public void onPWEvent(Bundle result) {
            int i;
            mStatusTime = 0;
            byte[] pwStatus = result.getByteArray("PWStatus");
            if (pwStatus != null) {
                int i1, i2;
                i1 = -1;
                i2 = -1;

                // update Rec icons
                for (i = 0; i < 8; i++) {
                    ImageView recIcon = (ImageView) findViewById(m_RecId[i]);
                    if (recIcon == null) continue;
                    if (i < pwStatus.length) {
                        if ((pwStatus[i] & 4) != 0) {
                            recIcon.setImageResource(m_RecImage[i]);
                        } else {
                            recIcon.setImageResource(m_NoRecImage[i]);
                        }
                        recIcon.setVisibility(View.VISIBLE);

                        int forcechannel = (pwStatus[i] >> 4) & 3;
                        if (i1 < 0) {
                            if (forcechannel == 0)
                                i1 = i;
                        }
                        if (i2 < 0) {
                            if (forcechannel == 1)
                                i2 = i;
                        }
                    } else {
                        recIcon.setVisibility(View.INVISIBLE);
                    }
                }

                // update PAN/BACK button image
                if (findViewById(R.id.pwcontrol).getVisibility() == View.VISIBLE) {     // button bar visible?
                    boolean rec, frec;

                    ImageButton button_cam = (ImageButton) findViewById(R.id.button_cam1);
                    if (i1 >= 0) {
                        rec = (pwStatus[i1] & 4) != 0;
                        frec = (pwStatus[i1] & 8) != 0;
                        if (rec == frec) {
                            if (rec) {
                                button_cam.setImageResource(R.drawable.pw_cam1_light);
                            } else {
                                button_cam.setImageResource(R.drawable.pw_cam1);
                            }
                        } else {
                            button_cam.setImageResource(R.drawable.pw_cam1_trans);
                        }
                    } else {
                        button_cam.setImageResource(R.drawable.pw_cam1);
                    }

                    button_cam = (ImageButton) findViewById(R.id.button_cam2);
                    if (i2 >= 0) {
                        rec = (pwStatus[i2] & 4) != 0;
                        frec = (pwStatus[i2] & 8) != 0;
                        if (rec == frec) {
                            if (rec) {
                                button_cam.setImageResource(R.drawable.pw_cam2_light);
                            } else {
                                button_cam.setImageResource(R.drawable.pw_cam2);
                            }
                        } else {
                            button_cam.setImageResource(R.drawable.pw_cam2_trans);
                        }
                    } else {
                        button_cam.setImageResource(R.drawable.pw_cam2);
                    }
                }
            }

            String msg = "";
            String DiskInfo = result.getString("DiskInfo", "");
            String di[] = DiskInfo.split("\n");

            boolean flashing = false;   // to flash message
            // disk info:   idx,mounted,totalspace,freespace,full,llen,nlen
            boolean d1avail = false;
            int disk = -1;
            int mounted = 0;
            int totalspace;
            int freespace;
            int full;
            int llen = 0;
            int nlen;
            int reserved;
            int msgcolor;
            float xfree = 0.0f;
            float lspace = 0.0f;

            boolean diskl1 = false;
            boolean diskl2 = false;
            int appmode = 0;

            if (di.length > 3 && di[3].length() > 4) {     // PWZ6 app mode
                Scanner scanner = new Scanner(di[3]);
                scanner.useDelimiter(",");
                if (scanner.hasNextInt()) disk = scanner.nextInt();

                if (disk == 100) {
                    if (scanner.hasNextInt()) appmode = scanner.nextInt();
                }
                // urn screen off (auto off)
                if( appmode != xappmod ) {
                    if (appmode <= 2) {
                        screen_KeepOn(true);               // put screen to sleep if device pass shutdown delay
                    } else {
                        setSreenTimeout(3000);
                        screen_KeepOn(false);               // put screen to sleep if device pass shutdown delay
                    }
                    xappmod = appmode;
                }
            }

            m_diskwaringflash = !m_diskwaringflash;

            // diskinfo:  disk,mounted,total,free,full,l_len,n_len,reserved

            /// DISPLAY DISK1
            if (di.length > 0 && di[0].length() > 10) {
                Scanner scanner = new Scanner(di[0]);
                scanner.useDelimiter(",");

                disk = -1;
                mounted = 0;
                totalspace = 1;
                freespace = 0;
                full = 1;
                llen = 0;
                nlen = 0;
                reserved = 1000;
                flashing = true;
                xfree = 0.0f;
                lspace = 0.0f;
                msgcolor = getResources().getColor(R.color.diskmsg_red) ;

                if (scanner.hasNextInt()) disk = scanner.nextInt();
                if (scanner.hasNextInt()) mounted = scanner.nextInt();
                if (scanner.hasNextInt()) totalspace = scanner.nextInt();
                if (scanner.hasNextInt()) freespace = scanner.nextInt();
                if (scanner.hasNextInt()) full = scanner.nextInt();
                if (scanner.hasNextInt()) llen = scanner.nextInt();
                if (scanner.hasNextInt()) nlen = scanner.nextInt();
                if (scanner.hasNextInt()) reserved = scanner.nextInt();

                if (disk == 0 && mounted != 0) {
                    if (full == 0) {
                        if( llen + nlen <= 0 )  { llen=0 ; nlen=1 ; }
                        lspace = ((float) totalspace - (float) freespace) * llen / (llen + nlen);
                        if (lspace < 0.0) lspace = 0.0f;
                        xfree = (float) totalspace - lspace - reserved;
                        if (xfree < 0.1) xfree = 0.1f;
                        msg = String.format("Disk1 : %.1fG", xfree / 1000.0f);

                        float freerate = xfree / (xfree + lspace);
                        if (freerate < 0.1f) {
                            msgcolor = getResources().getColor(R.color.diskmsg_red) ;
                        } else if (freerate < 0.3f) {
                            flashing = false;
                            msgcolor = getResources().getColor(R.color.diskmsg_amber) ;
                        } else {
                            flashing = false;
                            msgcolor = getResources().getColor(R.color.diskmsg_green) ;
                        }
                        d1avail = true;
                    } else {
                        msg = "Disk1 Full";
                    }
                } else {
                    msg = "Disk1 Not Available!";
                    llen = 0;
                }

                if (llen > 0) {
                    diskl1 = true;
                }

                if ( flashing && m_diskwaringflash ) {
                    ((TextView) findViewById(R.id.disk1msg)).setText("");
                } else {
                    ((TextView) findViewById(R.id.disk1msg)).setText(msg);
                }
                ((TextView) findViewById(R.id.disk1msg)).setTextColor(msgcolor);
            }

            /// DISPLAY DISK2
            if (di.length > 1 && di[1].length() > 10) {
                Scanner scanner = new Scanner(di[1]);
                scanner.useDelimiter(",");

                disk = -1;
                mounted = 0;
                totalspace = 1;
                freespace = 0;
                full = 1;
                llen = 0;
                nlen = 0;
                reserved = 1000;
                flashing = true;
                xfree = 0.0f;
                lspace = 0.0f;
                msgcolor = getResources().getColor(R.color.diskmsg_red) ;

                if (scanner.hasNextInt()) disk = scanner.nextInt();
                if (scanner.hasNextInt()) mounted = scanner.nextInt();
                if (scanner.hasNextInt()) totalspace = scanner.nextInt();
                if (scanner.hasNextInt()) freespace = scanner.nextInt();
                if (scanner.hasNextInt()) full = scanner.nextInt();
                if (scanner.hasNextInt()) llen = scanner.nextInt();
                if (scanner.hasNextInt()) nlen = scanner.nextInt();
                if (scanner.hasNextInt()) reserved = scanner.nextInt();

                if (disk == 1 && mounted != 0) {
                    if (full == 0) {
                        if( llen + nlen <= 0 )  { llen=0 ; nlen=1 ; }
                        lspace = ((float) totalspace - (float) freespace) * llen / (llen + nlen);
                        if (lspace < 0.0) lspace = 0.0f;
                        xfree = (float) totalspace - lspace - reserved;
                        if (xfree < 0.1) xfree = 0.1f;
                        msg = String.format("Disk2 : %.1fG", xfree / 1000.0f);

                        float freerate = xfree / (xfree + lspace);
                        if (freerate < 0.1f) {
                            msgcolor = getResources().getColor(R.color.diskmsg_red) ;
                        } else if (freerate < 0.3f) {
                            flashing = false;
                            msgcolor = getResources().getColor(R.color.diskmsg_amber) ;
                        } else {
                            flashing = false;
                            msgcolor = getResources().getColor(R.color.diskmsg_green) ;
                        }
                    } else {
                        msg = "Disk2 Full";
                    }
                } else {
                    msg = "Disk2 Not Available!";
                    llen = 0;
                }

                if (llen > 0) {
                    diskl2 = true;
                }

                if( d1avail ) msg="";

                if ( flashing && m_diskwaringflash ) {
                    ((TextView) findViewById(R.id.disk2msg)).setText("");
                } else {
                    ((TextView) findViewById(R.id.disk2msg)).setText(msg);
                }
                ((TextView) findViewById(R.id.disk2msg)).setTextColor(msgcolor);
            }

            if( appmode>=2 && ( diskl1 || diskl2 ) ) {
                // remove msg2
                ((TextView) findViewById(R.id.disk2msg)).setText("");
                msgcolor = getResources().getColor(R.color.diskmsg_white) ;
                ((TextView) findViewById(R.id.disk1msg)).setTextColor(msgcolor);

                msg="Video available on " ;
                if( diskl1 ) {
                    msg+="DISK1" ;
                }
                if( diskl2 ) {
                    if( diskl1 ) msg+=" & " ;
                    msg+="DISK2" ;
                }
                msg+=" !" ;
                ((TextView) findViewById(R.id.disk1msg)).setText(msg);

            }
        }
    } ;


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_liveview, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            //Intent intent = new Intent(getBaseContext(), Launcher.class);
            Intent intent = new Intent(getBaseContext(), SettingsActivity.class);
            startActivity(intent);
            return true;
        }
        else if (id == R.id.action_playback) {
            Intent intent = new Intent(getBaseContext(), Playback.class);
            startActivity(intent);
            finish();
            return true;
        }
        else if ( id == R.id.device_setup) {
            mPwProtocol.GetWebUrl(new PWProtocol.PWListener() {
                @Override
                public void onPWEvent(Bundle result) {
                    if (result != null) {
                        String url = result.getString("URL");
                        if (url != null) {
                            Intent intent = new Intent(getBaseContext(), PwWebView.class);
                            intent.putExtra("URL", url+"login.html");
                            intent.putExtra("TITLE", "Device Setup");
                            startActivity(intent);
                        }
                    }
                }

            });
        }
        else if ( id == R.id.video_archive) {
            Intent intent = new Intent(getBaseContext(), ArchiveActivity.class);
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void savePref(){
        // save current channel
        channel = m_channel ;
    }

}