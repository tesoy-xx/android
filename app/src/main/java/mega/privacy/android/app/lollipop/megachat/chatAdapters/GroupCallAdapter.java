package mega.privacy.android.app.lollipop.megachat.chatAdapters;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.PixelCopy;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Random;

import mega.privacy.android.app.MegaApplication;
import mega.privacy.android.app.R;
import mega.privacy.android.app.components.CustomizedGridRecyclerView;
import mega.privacy.android.app.components.RoundedImageView;
import mega.privacy.android.app.lollipop.listeners.ChatNonContactNameListener;
import mega.privacy.android.app.lollipop.listeners.ChatUserAvatarListener;
import mega.privacy.android.app.lollipop.listeners.GroupCallListener;
import mega.privacy.android.app.lollipop.listeners.UserAvatarListener;
import mega.privacy.android.app.lollipop.megachat.calls.ChatCallActivity;
import mega.privacy.android.app.lollipop.megachat.calls.InfoPeerGroupCall;
import mega.privacy.android.app.lollipop.megachat.calls.MegaSurfaceRendererGroup;
import mega.privacy.android.app.utils.ThumbnailUtilsLollipop;
import mega.privacy.android.app.utils.Util;
import nz.mega.sdk.MegaApiAndroid;
import nz.mega.sdk.MegaChatApiAndroid;
import nz.mega.sdk.MegaChatApiJava;
import nz.mega.sdk.MegaChatVideoListenerInterface;
import nz.mega.sdk.MegaNode;

import static android.view.View.GONE;

public class GroupCallAdapter extends RecyclerView.Adapter<GroupCallAdapter.ViewHolderGroupCall> implements MegaSurfaceRendererGroup.MegaSurfaceRendererGroupListener {

    public static final int ITEM_VIEW_TYPE_LIST = 0;
    public static final int ITEM_VIEW_TYPE_GRID = 1;

    Context context;
    MegaApiAndroid megaApi;
    MegaChatApiAndroid megaChatApi = null;
    Display display;
    DisplayMetrics outMetrics;
    float density;
    float scaleW;
    float scaleH;
    float widthScreenPX, heightScreenPX;
    boolean isCallInProgress = false;

    RecyclerView recyclerViewFragment;

    ArrayList<InfoPeerGroupCall> peers;
    long chatId;

    int maxScreenWidth, maxScreenHeight;
    boolean avatarRequested = false;
    boolean isGrid = true;
    boolean isManualMode = false;
    int statusBarHeight = 0;

    public GroupCallAdapter(Context context, RecyclerView recyclerView, ArrayList<InfoPeerGroupCall> peers, long chatId, boolean isCallInProgress, boolean isGrid) {

        if(peers!=null){
            log("GroupCallAdapter(peers: "+peers.size()+")");
        }
        this.context = context;
        this.recyclerViewFragment = recyclerView;
        this.peers = peers;
        this.chatId = chatId;
        this.isCallInProgress = isCallInProgress;
        this.isGrid = isGrid;

        MegaApplication app = (MegaApplication) ((Activity) context).getApplication();
        if (megaApi == null) {
            megaApi = app.getMegaApi();
        }

        log("retryPendingConnections()");
        if (megaApi != null) {
            log("---------retryPendingConnections");
            megaApi.retryPendingConnections();
        }

        if (megaChatApi == null) {
            megaChatApi = app.getMegaChatApi();
        }
    }

    public class ViewHolderGroupCall extends RecyclerView.ViewHolder{

        RelativeLayout rlGeneral;
        RelativeLayout greenLayer;
        RelativeLayout avatarMicroLayout;
        RelativeLayout avatarLayout;
        RoundedImageView avatarImage;
        ImageView microAvatar;
        ImageView microSurface;
        RelativeLayout qualityLayout;
        RelativeLayout qualityIcon;
        TextView avatarInitialLetter;
        RelativeLayout parentSurfaceView;
        RelativeLayout surfaceMicroLayout;
        public ViewHolderGroupCall(View itemView) {
            super(itemView);
        }

    }

    public class ViewHolderGroupCallGrid extends ViewHolderGroupCall{
        public ViewHolderGroupCallGrid(View v) {
            super(v);
        }
    }

    ViewHolderGroupCallGrid holderGrid = null;

    @Override public GroupCallAdapter.ViewHolderGroupCall onCreateViewHolder(ViewGroup parent, int viewType) {
        log("onCreateViewHolder()");

        display = ((ChatCallActivity) context).getWindowManager().getDefaultDisplay();
        outMetrics = new DisplayMetrics();
        display.getMetrics(outMetrics);
        widthScreenPX = outMetrics.widthPixels;
        heightScreenPX = outMetrics.heightPixels;
        density = context.getResources().getDisplayMetrics().density;
        scaleW = Util.getScaleW(outMetrics, density);
        scaleH = Util.getScaleH(outMetrics, density);

        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_camera_group_call, parent, false);

        int resourceId = context.getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            statusBarHeight = context.getResources().getDimensionPixelSize(resourceId);
        }
        maxScreenHeight = (int)heightScreenPX - statusBarHeight;
        maxScreenWidth = (int)widthScreenPX;

        holderGrid = new ViewHolderGroupCallGrid(v);

        holderGrid.rlGeneral = (RelativeLayout) v.findViewById(R.id.general);
        holderGrid.greenLayer = (RelativeLayout) v.findViewById(R.id.green_layer);
        holderGrid.surfaceMicroLayout = (RelativeLayout) v.findViewById(R.id.rl_surface_and_micro);

        holderGrid.parentSurfaceView = (RelativeLayout) v.findViewById(R.id.parent_surface_view);
        holderGrid.avatarMicroLayout = (RelativeLayout) v.findViewById(R.id.layout_avatar_micro);

        holderGrid.avatarLayout = (RelativeLayout) v.findViewById(R.id.avatar_rl);
        RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams)holderGrid.avatarLayout.getLayoutParams();
        layoutParams.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);
        holderGrid.avatarLayout.setLayoutParams(layoutParams);

        holderGrid.microAvatar = (ImageView) v.findViewById(R.id.micro_avatar);
        holderGrid.microSurface = (ImageView) v.findViewById(R.id.micro_surface_view);
        holderGrid.qualityLayout = (RelativeLayout) v.findViewById(R.id.rl_quality);
        holderGrid.qualityIcon = (RelativeLayout) v.findViewById(R.id.quality_icon);
        holderGrid.avatarImage = (RoundedImageView) v.findViewById(R.id.avatar_image);
        holderGrid.avatarInitialLetter = (TextView) v.findViewById(R.id.avatar_initial_letter);
        holderGrid.avatarImage.setImageBitmap(null);
        holderGrid.avatarInitialLetter.setText("");

        v.setTag(holderGrid);
        return holderGrid;
    }

    @Override
    public void onBindViewHolder(ViewHolderGroupCall holder, int position) {
        ViewHolderGroupCallGrid holderGrid2 = (ViewHolderGroupCallGrid) holder;
        onBindViewHolderGrid(holderGrid2, position);
    }

    public void onBindViewHolderGrid (final ViewHolderGroupCallGrid holder, final int position){
        log("onBindViewHolderGrid() - position: "+position);

        final InfoPeerGroupCall peer = getNodeAt(position);
        if (peer == null){
            return;
        }

        int numPeersOnCall = getItemCount();
        log("onBindViewHolderGrid() - (peerId = "+peer.getPeerId()+", clientId = "+peer.getClientId()+") of numPeersOnCall: "+numPeersOnCall);


        if(isGrid){
            CustomizedGridRecyclerView.LayoutParams lp = (CustomizedGridRecyclerView.LayoutParams) holder.rlGeneral.getLayoutParams();

            if(numPeersOnCall < 4){
                lp.height = maxScreenHeight/numPeersOnCall;
                lp.width = maxScreenWidth;

            }else if((numPeersOnCall >= 4) && (numPeersOnCall < 7)){
                lp.height = maxScreenWidth/2;
                lp.width = maxScreenWidth/2;
                if((peers.size()==5)  && (peer.getPeerId() == megaChatApi.getMyUserHandle()) && (peer.getClientId() == megaChatApi.getMyClientidHandle(chatId))){
                    ViewGroup.LayoutParams layoutParamsPeer = (ViewGroup.LayoutParams) holder.rlGeneral.getLayoutParams();
                    layoutParamsPeer.width = maxScreenWidth;
                    layoutParamsPeer.height = maxScreenWidth/2;
                    holder.rlGeneral.setLayoutParams(layoutParamsPeer);
                }
            }else{
                lp.height = Util.scaleWidthPx(90, outMetrics);
                lp.width = Util.scaleWidthPx(90, outMetrics);
            }
            holder.rlGeneral.setLayoutParams(lp);
        }else{
            RecyclerView.LayoutParams lp = (RecyclerView.LayoutParams) holder.rlGeneral.getLayoutParams();

            if(numPeersOnCall < 4){
                lp.height = maxScreenHeight/numPeersOnCall;
                lp.width = maxScreenWidth;

            }else if((numPeersOnCall >= 4) && (numPeersOnCall < 7)){
                lp.height = maxScreenWidth/2;
                lp.width = maxScreenWidth/2;
                if((peers.size()==5)  && (peer.getPeerId() == megaChatApi.getMyUserHandle()) && (peer.getClientId() == megaChatApi.getMyClientidHandle(chatId))){
                    ViewGroup.LayoutParams layoutParamsPeer = (ViewGroup.LayoutParams) holder.rlGeneral.getLayoutParams();
                    layoutParamsPeer.width = maxScreenWidth;
                    layoutParamsPeer.height = maxScreenWidth/2;
                    holder.rlGeneral.setLayoutParams(layoutParamsPeer);
                }
            }else{
                lp.height = Util.scaleWidthPx(90, outMetrics);
                lp.width = Util.scaleWidthPx(90, outMetrics);
            }
            holder.rlGeneral.setLayoutParams(lp);
        }

        if(isCallInProgress){
            holder.rlGeneral.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if(getItemCount() < 7){
                        ((ChatCallActivity) context).remoteCameraClick();
                    }else{
                        ((ChatCallActivity) context).itemClicked(peer);
                    }
                }
            });
        }else{
            holder.rlGeneral.setOnClickListener(null);
        }

        holder.avatarImage.setImageBitmap(null);
        holder.avatarInitialLetter.setText("");

        if(peer.isVideoOn()) {
            log("(peerid = "+peer.getPeerId()+", clientId = "+peer.getClientId()+") VIDEO ON pos: "+position);

            holder.avatarMicroLayout.setVisibility(GONE);
            holder.microAvatar.setVisibility(View.GONE);

            if(numPeersOnCall == 1){

                //Surface Layout:
                RelativeLayout.LayoutParams layoutParamsSurface = (RelativeLayout.LayoutParams) holder.parentSurfaceView.getLayoutParams();
                layoutParamsSurface.width = maxScreenWidth;
                layoutParamsSurface.height = maxScreenWidth;
                layoutParamsSurface.addRule(RelativeLayout.CENTER_HORIZONTAL,RelativeLayout.TRUE);
                layoutParamsSurface.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);
                layoutParamsSurface.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, 0);
                layoutParamsSurface.addRule(RelativeLayout.ALIGN_PARENT_TOP, 0);
                holder.parentSurfaceView.setLayoutParams(layoutParamsSurface);

                RelativeLayout.LayoutParams paramsQualityLayout = new RelativeLayout.LayoutParams(holder.qualityLayout.getLayoutParams());
                paramsQualityLayout.height = maxScreenWidth;
                paramsQualityLayout.width = maxScreenWidth;
                paramsQualityLayout.addRule(RelativeLayout.ALIGN_TOP, R.id.parent_surface_view);
                paramsQualityLayout.addRule(RelativeLayout.ALIGN_LEFT, R.id.parent_surface_view);
                holder.qualityLayout.setLayoutParams(paramsQualityLayout);

            }else if(numPeersOnCall == 2){

                //Surface Layout:
                RelativeLayout.LayoutParams layoutParamsSurface = (RelativeLayout.LayoutParams) holder.parentSurfaceView.getLayoutParams();
                layoutParamsSurface.width = maxScreenHeight/numPeersOnCall;
                layoutParamsSurface.height = maxScreenHeight/numPeersOnCall;
                layoutParamsSurface.addRule(RelativeLayout.CENTER_HORIZONTAL,RelativeLayout.TRUE);
                layoutParamsSurface.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);
                layoutParamsSurface.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, 0);
                layoutParamsSurface.addRule(RelativeLayout.ALIGN_PARENT_TOP, 0);
                holder.parentSurfaceView.setLayoutParams(layoutParamsSurface);

                RelativeLayout.LayoutParams paramsQualityLayout = new RelativeLayout.LayoutParams(holder.qualityLayout.getLayoutParams());
                paramsQualityLayout.height = maxScreenHeight/numPeersOnCall;
                paramsQualityLayout.width = maxScreenHeight/numPeersOnCall;
                paramsQualityLayout.addRule(RelativeLayout.ALIGN_TOP, R.id.parent_surface_view);
                paramsQualityLayout.addRule(RelativeLayout.ALIGN_LEFT, R.id.parent_surface_view);
                holder.qualityLayout.setLayoutParams(paramsQualityLayout);

            }else if(numPeersOnCall == 3){

                //Surface Layout:
                RelativeLayout.LayoutParams layoutParamsSurface = (RelativeLayout.LayoutParams) holder.parentSurfaceView.getLayoutParams();
                layoutParamsSurface.width = maxScreenHeight/numPeersOnCall;
                layoutParamsSurface.height = maxScreenHeight/numPeersOnCall;
                layoutParamsSurface.addRule(RelativeLayout.CENTER_HORIZONTAL, RelativeLayout.TRUE);
                layoutParamsSurface.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);
                layoutParamsSurface.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, 0);
                layoutParamsSurface.addRule(RelativeLayout.ALIGN_PARENT_TOP, 0);
                holder.parentSurfaceView.setLayoutParams(layoutParamsSurface);

                RelativeLayout.LayoutParams paramsQualityLayout = new RelativeLayout.LayoutParams(holder.qualityLayout.getLayoutParams());
                paramsQualityLayout.height = maxScreenHeight/numPeersOnCall;
                paramsQualityLayout.width = maxScreenHeight/numPeersOnCall;
                paramsQualityLayout.addRule(RelativeLayout.ALIGN_TOP, R.id.parent_surface_view);
                paramsQualityLayout.addRule(RelativeLayout.ALIGN_LEFT, R.id.parent_surface_view);
                holder.qualityLayout.setLayoutParams(paramsQualityLayout);

            }else if(numPeersOnCall == 4){

                //Surface Layout:
                RelativeLayout.LayoutParams layoutParamsSurface = (RelativeLayout.LayoutParams) holder.parentSurfaceView.getLayoutParams();
                layoutParamsSurface.width = maxScreenWidth/2;
                layoutParamsSurface.height = maxScreenWidth/2;
                layoutParamsSurface.addRule(RelativeLayout.CENTER_HORIZONTAL, RelativeLayout.TRUE);

                if((position < 2)){
                    layoutParamsSurface.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE);
                }else{
                    layoutParamsSurface.addRule(RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE);

                }
                holder.parentSurfaceView.setLayoutParams(layoutParamsSurface);

                RelativeLayout.LayoutParams paramsQualityLayout = new RelativeLayout.LayoutParams(holder.qualityLayout.getLayoutParams());
                paramsQualityLayout.height = maxScreenWidth/2;
                paramsQualityLayout.width = maxScreenWidth/2;
                paramsQualityLayout.addRule(RelativeLayout.ALIGN_TOP, R.id.parent_surface_view);
                paramsQualityLayout.addRule(RelativeLayout.ALIGN_LEFT, R.id.parent_surface_view);
                holder.qualityLayout.setLayoutParams(paramsQualityLayout);

            }else if(numPeersOnCall == 5){

                //Surface Layout:
                RelativeLayout.LayoutParams layoutParamsSurface = (RelativeLayout.LayoutParams) holder.parentSurfaceView.getLayoutParams();
                layoutParamsSurface.width = maxScreenWidth/2;
                layoutParamsSurface.height = maxScreenWidth/2;
                layoutParamsSurface.addRule(RelativeLayout.CENTER_HORIZONTAL, RelativeLayout.TRUE);
                layoutParamsSurface.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, 0);
                layoutParamsSurface.addRule(RelativeLayout.ALIGN_PARENT_TOP, 0);
                holder.parentSurfaceView.setLayoutParams(layoutParamsSurface);

                RelativeLayout.LayoutParams paramsQualityLayout = new RelativeLayout.LayoutParams(holder.qualityLayout.getLayoutParams());
                paramsQualityLayout.height = maxScreenWidth/2;
                paramsQualityLayout.width = maxScreenWidth/2;
                paramsQualityLayout.addRule(RelativeLayout.ALIGN_TOP, R.id.parent_surface_view);
                paramsQualityLayout.addRule(RelativeLayout.ALIGN_LEFT, R.id.parent_surface_view);
                holder.qualityLayout.setLayoutParams(paramsQualityLayout);

            }else if(numPeersOnCall == 6){

                //Surface Layout:
                RelativeLayout.LayoutParams layoutParamsSurface = (RelativeLayout.LayoutParams) holder.parentSurfaceView.getLayoutParams();
                layoutParamsSurface.width = maxScreenWidth/2;
                layoutParamsSurface.height = maxScreenWidth/2;
                layoutParamsSurface.addRule(RelativeLayout.CENTER_HORIZONTAL, RelativeLayout.TRUE);
                layoutParamsSurface.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, 0);
                layoutParamsSurface.addRule(RelativeLayout.ALIGN_PARENT_TOP, 0);
                holder.parentSurfaceView.setLayoutParams(layoutParamsSurface);

                RelativeLayout.LayoutParams paramsQualityLayout = new RelativeLayout.LayoutParams(holder.qualityLayout.getLayoutParams());
                paramsQualityLayout.height = maxScreenWidth/2;
                paramsQualityLayout.width = maxScreenWidth/2;
                paramsQualityLayout.addRule(RelativeLayout.ALIGN_TOP, R.id.parent_surface_view);
                paramsQualityLayout.addRule(RelativeLayout.ALIGN_LEFT, R.id.parent_surface_view);
                holder.qualityLayout.setLayoutParams(paramsQualityLayout);
            }

            //Listener && SurfaceView
            if(peer.getListener() == null){
                log("( peerId = "+peer.getPeerId()+", clientId = "+peer.getClientId()+") VIDEO ON- listener == null ");
                holder.parentSurfaceView.removeAllViews();
                TextureView myTexture = new TextureView(context);
                myTexture.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));
                myTexture.setAlpha(1.0f);
                myTexture.setRotation(0);
                GroupCallListener listenerPeer = new GroupCallListener(context, myTexture, peer.getPeerId(), peer.getClientId());
                peer.setListener(listenerPeer);

                if((peer.getPeerId() == megaChatApi.getMyUserHandle()) && (peer.getClientId() == megaChatApi.getMyClientidHandle(chatId))){

                    megaChatApi.addChatLocalVideoListener(chatId, peer.getListener());
                } else {
                    megaChatApi.addChatRemoteVideoListener(chatId, peer.getPeerId(), peer.getClientId(), peer.getListener());
                }

                if(numPeersOnCall < 7){
                    peer.getListener().getLocalRenderer().addListener(null);
                }else{
                    peer.getListener().getLocalRenderer().addListener(this);
                }

                holder.parentSurfaceView.addView(peer.getListener().getSurfaceView());

            }else{

                log("(peerId = "+peer.getPeerId()+", clientId = "+peer.getClientId()+") VIDEO ON - listener != null");
                if(holder.parentSurfaceView.getChildCount() == 0){
                    if(peer.getListener().getSurfaceView().getParent()!=null){
                        if(peer.getListener().getSurfaceView().getParent().getParent()!=null){
                            ((ViewGroup)peer.getListener().getSurfaceView().getParent()).removeView(peer.getListener().getSurfaceView());
                            holder.parentSurfaceView.addView(peer.getListener().getSurfaceView());
                        }else{
                            holder.parentSurfaceView.addView(peer.getListener().getSurfaceView());
                        }
                    }else{
                        holder.parentSurfaceView.addView(peer.getListener().getSurfaceView());
                    }
                }else{
                    if(holder.parentSurfaceView.getChildAt(0).equals(peer.getListener().getSurfaceView())){
                    }else{
                        //Remove items of parent
                        holder.parentSurfaceView.removeAllViews();
                        //Remove parent of Surface
                        if(peer.getListener().getSurfaceView().getParent()!=null){
                            if(peer.getListener().getSurfaceView().getParent().getParent()!=null){
                                ((ViewGroup)peer.getListener().getSurfaceView().getParent()).removeView(peer.getListener().getSurfaceView());
                                holder.parentSurfaceView.addView(peer.getListener().getSurfaceView());
                            }else{
                                holder.parentSurfaceView.addView(peer.getListener().getSurfaceView());
                            }
                        }else{
                            holder.parentSurfaceView.addView(peer.getListener().getSurfaceView());
                        }
                    }
                }

                if(peer.getListener().getHeight() != 0){
                    peer.getListener().setHeight(0);
                }
                if(peer.getListener().getWidth() != 0){
                    peer.getListener().setWidth(0);
                }
            }

            holder.surfaceMicroLayout.setVisibility(View.VISIBLE);

            //Audio icon:
            if(peers.size() < 7){
                RelativeLayout.LayoutParams paramsMicroSurface = new RelativeLayout.LayoutParams(holder.microSurface.getLayoutParams());
                paramsMicroSurface.height = Util.scaleWidthPx(24, outMetrics);
                paramsMicroSurface.width = Util.scaleWidthPx(24, outMetrics);
                paramsMicroSurface.setMargins(0, Util.scaleWidthPx(15, outMetrics),  Util.scaleWidthPx(15, outMetrics), 0);
                paramsMicroSurface.addRule(RelativeLayout.ALIGN_TOP, R.id.parent_surface_view);
                paramsMicroSurface.addRule(RelativeLayout.ALIGN_RIGHT, R.id.parent_surface_view);
                holder.microSurface.setLayoutParams(paramsMicroSurface);

                RelativeLayout.LayoutParams paramsQuality = new RelativeLayout.LayoutParams(holder.qualityIcon.getLayoutParams());
                paramsQuality.height = Util.scaleWidthPx(24, outMetrics);
                paramsQuality.width = Util.scaleWidthPx(24, outMetrics);
                paramsQuality.setMargins(Util.scaleWidthPx(15, outMetrics), 0,  0, Util.scaleWidthPx(15, outMetrics));
                paramsQuality.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE);
                paramsQuality.addRule(RelativeLayout.ALIGN_PARENT_LEFT,  RelativeLayout.TRUE);
                holder.qualityIcon.setLayoutParams(paramsQuality);
            }else{
                RelativeLayout.LayoutParams paramsMicroSurface = new RelativeLayout.LayoutParams(holder.microSurface.getLayoutParams());
                paramsMicroSurface.height = Util.scaleWidthPx(15, outMetrics);
                paramsMicroSurface.width = Util.scaleWidthPx(15, outMetrics);
                paramsMicroSurface.setMargins(0,  Util.scaleWidthPx(7, outMetrics),  Util.scaleWidthPx(7, outMetrics), 0);
                paramsMicroSurface.addRule(RelativeLayout.ALIGN_TOP, R.id.parent_surface_view);
                paramsMicroSurface.addRule(RelativeLayout.ALIGN_RIGHT, R.id.parent_surface_view);
                holder.microSurface.setLayoutParams(paramsMicroSurface);

                RelativeLayout.LayoutParams paramsQuality = new RelativeLayout.LayoutParams(holder.qualityIcon.getLayoutParams());
                paramsQuality.height = Util.scaleWidthPx(20, outMetrics);
                paramsQuality.width = Util.scaleWidthPx(20, outMetrics);
                paramsQuality.setMargins(Util.scaleWidthPx(7, outMetrics), 0,  0, Util.scaleWidthPx(7, outMetrics));
                paramsQuality.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE);
                paramsQuality.addRule(RelativeLayout.ALIGN_PARENT_LEFT,  RelativeLayout.TRUE);
                holder.qualityIcon.setLayoutParams(paramsQuality);
            }

            if(peer.isAudioOn()){
                holder.microSurface.setVisibility(View.GONE);
            }else{
                if(isCallInProgress) {
                    holder.microSurface.setVisibility(View.VISIBLE);
                }else{
                    holder.microSurface.setVisibility(View.GONE);
                }
            }

            if(peer.isGoodQuality()){
                holder.qualityLayout.setVisibility(View.GONE);
            }else{
                if(isCallInProgress) {
                    holder.qualityLayout.setVisibility(View.VISIBLE);
                }else{
                    holder.qualityLayout.setVisibility(View.GONE);
                }
            }

            //Green Layer:
            if(numPeersOnCall < 7){
                holder.greenLayer.setVisibility(View.GONE);
                peer.setGreenLayer(false);
            }else{
                if(peer.hasGreenLayer()){
                    if(isManualMode){
                        holder.greenLayer.setBackground(ContextCompat.getDrawable(context, R.drawable.border_green_layer_selected));
                    }else {
                        holder.greenLayer.setBackground(ContextCompat.getDrawable(context, R.drawable.border_green_layer));
                    }
                    holder.greenLayer.setVisibility(View.VISIBLE);
                }else{
                    holder.greenLayer.setVisibility(View.GONE);
                }
            }

        }else{
            log("(peerId = "+peer.getPeerId()+", clientId = "+peer.getPeerId()+") VIDEO OFF");
            //Avatar:
            if((peer.getPeerId() == megaChatApi.getMyUserHandle()) && (peer.getClientId() == megaChatApi.getMyClientidHandle(chatId))){
                setProfileMyAvatarGroupCall(holder);
            }else{
                setProfileParticipantAvatarGroupCall(peer.getPeerId(), peer.getName(), holder);
            }

            holder.qualityLayout.setVisibility(GONE);

            //Remove SurfaceView && Listener:
            holder.surfaceMicroLayout.setVisibility(GONE);
            if(peer.getListener() != null){
                if((peer.getPeerId() == megaChatApi.getMyUserHandle()) && (peer.getClientId() == megaChatApi.getMyClientidHandle(chatId))){
                    megaChatApi.removeChatVideoListener(chatId, -1, -1, peer.getListener());
                }else{
                    megaChatApi.removeChatVideoListener(chatId, peer.getPeerId(), peer.getClientId(), peer.getListener());
                }
                if(holder.parentSurfaceView.getChildCount() == 0){
                    if(peer.getListener().getSurfaceView().getParent()!=null){
                        if(peer.getListener().getSurfaceView().getParent().getParent()!=null){
                            ((ViewGroup)peer.getListener().getSurfaceView().getParent()).removeView(peer.getListener().getSurfaceView());
                        }
                    }
                }else{
                    holder.parentSurfaceView.removeAllViews();

                    if(peer.getListener().getSurfaceView().getParent()!=null){
                        if(peer.getListener().getSurfaceView().getParent().getParent()!=null){
                            ((ViewGroup)peer.getListener().getSurfaceView().getParent()).removeView(peer.getListener().getSurfaceView());

                        }
                    }
                }
                peer.setListener(null);
            }
            holder.avatarMicroLayout.setVisibility(View.VISIBLE);


            //Micro icon:
            if(numPeersOnCall < 7){
                RelativeLayout.LayoutParams paramsMicroAvatar = new RelativeLayout.LayoutParams(holder.microAvatar.getLayoutParams());
                paramsMicroAvatar.height = Util.scaleWidthPx(24, outMetrics);
                paramsMicroAvatar.width = Util.scaleWidthPx(24, outMetrics);
                paramsMicroAvatar.setMargins(Util.scaleWidthPx(10, outMetrics), 0, 0, 0);
                paramsMicroAvatar.addRule(RelativeLayout.RIGHT_OF, R.id.avatar_rl);
                paramsMicroAvatar.addRule(RelativeLayout.ALIGN_TOP, R.id.avatar_rl);
                holder.microAvatar.setLayoutParams(paramsMicroAvatar);

                ViewGroup.LayoutParams paramsAvatarImage = (ViewGroup.LayoutParams) holder.avatarImage.getLayoutParams();
                paramsAvatarImage.width = Util.scaleWidthPx(88, outMetrics);
                paramsAvatarImage.height = Util.scaleWidthPx(88, outMetrics);
                holder.avatarImage.setLayoutParams(paramsAvatarImage);
                holder.avatarInitialLetter.setTextSize(TypedValue.COMPLEX_UNIT_SP, 50f);
            }else{
                RelativeLayout.LayoutParams paramsMicroAvatar = new RelativeLayout.LayoutParams(holder.microAvatar.getLayoutParams());
                paramsMicroAvatar.height = Util.scaleWidthPx(15, outMetrics);
                paramsMicroAvatar.width = Util.scaleWidthPx(15, outMetrics);
                paramsMicroAvatar.setMargins(0, 0, 0, 0);
                paramsMicroAvatar.addRule(RelativeLayout.RIGHT_OF, R.id.avatar_rl);
                paramsMicroAvatar.addRule(RelativeLayout.ALIGN_TOP, R.id.avatar_rl);
                holder.microAvatar.setLayoutParams(paramsMicroAvatar);

                ViewGroup.LayoutParams paramsAvatarImage = (ViewGroup.LayoutParams) holder.avatarImage.getLayoutParams();
                paramsAvatarImage.width = Util.scaleWidthPx(60, outMetrics);
                paramsAvatarImage.height = Util.scaleWidthPx(60, outMetrics);
                holder.avatarImage.setLayoutParams(paramsAvatarImage);
                holder.avatarInitialLetter.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30f);
            }
            if(peer.isAudioOn()){
                holder.microAvatar.setVisibility(View.GONE);
            }else{
                if(isCallInProgress){
                    holder.microAvatar.setVisibility(View.VISIBLE);
                }else{
                    holder.microAvatar.setVisibility(View.GONE);
                }
            }

            //Green Layer:
            if(numPeersOnCall < 7){
                holder.greenLayer.setVisibility(View.GONE);
                peer.setGreenLayer(false);
            }else{
                if(peer.hasGreenLayer()){
                    if(isManualMode){
                        holder.greenLayer.setBackground(ContextCompat.getDrawable(context, R.drawable.border_green_layer_selected));
                    }else {
                        holder.greenLayer.setBackground(ContextCompat.getDrawable(context, R.drawable.border_green_layer));
                    }
                    holder.greenLayer.setVisibility(View.VISIBLE);
                }else{
                    holder.greenLayer.setVisibility(View.GONE);
                }
            }

        }
    }

    @Override
    public int getItemCount() {
        if (peers != null){
            return peers.size();
        }else{
            return 0;
        }
    }

    public Object getItem(int position) {
        if (peers != null){
            return peers.get(position);
        }
        return null;
    }

    public InfoPeerGroupCall getNodeAt(int position) {
        try {
            if (peers != null) {
                return peers.get(position);
            }
        } catch (IndexOutOfBoundsException e) {}
        return null;
    }

    //Group call: default my avatar
    public void createMyDefaultAvatarGroupCall(ViewHolderGroupCall holder) {
        log("createMyDefaultAvatarGroupCall()");

        String myFullName = megaChatApi.getMyFullname();
        String myFirstLetter=myFullName.charAt(0) + "";
        myFirstLetter = myFirstLetter.toUpperCase(Locale.getDefault());
        long userHandle = megaChatApi.getMyUserHandle();

        Bitmap defaultAvatar = Bitmap.createBitmap(outMetrics.widthPixels, outMetrics.widthPixels, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(defaultAvatar);
        Paint p = new Paint();
        p.setAntiAlias(true);
        p.setColor(Color.TRANSPARENT);

        String color = megaApi.getUserAvatarColor(MegaApiAndroid.userHandleToBase64(userHandle));
        if(color!=null){
            p.setColor(Color.parseColor(color));
        }else{
            p.setColor(ContextCompat.getColor(context, R.color.lollipop_primary_color));
        }

        int radius;
        if (defaultAvatar.getWidth() < defaultAvatar.getHeight()) {
            radius = defaultAvatar.getWidth() / 2;
        }else {
            radius = defaultAvatar.getHeight() / 2;
        }
        c.drawCircle(defaultAvatar.getWidth()/2, defaultAvatar.getHeight()/2, radius, p);
        holder.avatarImage.setImageBitmap(defaultAvatar);
        holder.avatarInitialLetter.setText(myFirstLetter);
        holder.avatarInitialLetter.setVisibility(View.VISIBLE);
    }
    //Group call: my avatar
   public void setProfileMyAvatarGroupCall(ViewHolderGroupCall holder) {
        log("setProfileMyAvatarGroupCall()");
       Bitmap myBitmap = null;
       File avatar = null;
       if (context != null) {
           if (context.getExternalCacheDir() != null) {
               avatar = new File(context.getExternalCacheDir().getAbsolutePath(), megaChatApi.getMyEmail() + ".jpg");
           } else {
               avatar = new File(context.getCacheDir().getAbsolutePath(), megaChatApi.getMyEmail() + ".jpg");
           }
       }
       if (avatar.exists()) {
           if (avatar.length() > 0) {
               BitmapFactory.Options bOpts = new BitmapFactory.Options();
               bOpts.inPurgeable = true;
               bOpts.inInputShareable = true;
               myBitmap = BitmapFactory.decodeFile(avatar.getAbsolutePath(), bOpts);
               myBitmap = ThumbnailUtilsLollipop.getRoundedRectBitmap(context, myBitmap, 3);
               if (myBitmap != null) {
                       holder.avatarImage.setImageBitmap(myBitmap);
                       holder.avatarInitialLetter.setVisibility(GONE);
               }else{
                   createMyDefaultAvatarGroupCall(holder);
               }
           }else {
               createMyDefaultAvatarGroupCall(holder);
           }
       }else {
           createMyDefaultAvatarGroupCall(holder);
       }
   }

    //Group call: default participant avatar
    public void createDefaultParticipantAvatarGroupCall(long peerid, ViewHolderGroupCall holder, String fullName, String mail){
        log("createDefaultParticipantAvatarGroupCall()");

        Bitmap defaultAvatar = Bitmap.createBitmap(outMetrics.widthPixels, outMetrics.widthPixels, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(defaultAvatar);
        Paint p = new Paint();
        p.setAntiAlias(true);

        String color = megaApi.getUserAvatarColor(MegaApiAndroid.userHandleToBase64(peerid));
        if (color != null) {
            p.setColor(Color.parseColor(color));
        } else {
            p.setColor(ContextCompat.getColor(context, R.color.lollipop_primary_color));
        }

        int radius;
        if (defaultAvatar.getWidth() < defaultAvatar.getHeight()) {
            radius = defaultAvatar.getWidth() / 2;
        }else {
            radius = defaultAvatar.getHeight() / 2;
        }

        c.drawCircle(defaultAvatar.getWidth()/2, defaultAvatar.getHeight()/2, radius, p);
        holder.avatarImage.setVisibility(View.VISIBLE);
        holder.avatarImage.setImageBitmap(defaultAvatar);

        Display display = ((Activity)context).getWindowManager().getDefaultDisplay();
        outMetrics = new DisplayMetrics ();
        display.getMetrics(outMetrics);

        boolean setInitialByMail = false;
        if (fullName != null){
            if (fullName.trim().length() > 0){
                String firstLetter = fullName.charAt(0) + "";
                firstLetter = firstLetter.toUpperCase(Locale.getDefault());
                holder.avatarInitialLetter.setText(firstLetter);
                holder.avatarInitialLetter.setTextColor(Color.WHITE);
                holder.avatarInitialLetter.setVisibility(View.VISIBLE);
            }else{
                setInitialByMail=true;
            }
        }else{
            setInitialByMail=true;
        }
        if(setInitialByMail){
            if (mail != null){
                if (mail.length() > 0){
                    String firstLetter = mail.charAt(0) + "";
                    firstLetter = firstLetter.toUpperCase(Locale.getDefault());
                    holder.avatarInitialLetter.setText(firstLetter);
                    holder.avatarInitialLetter.setTextColor(Color.WHITE);
                    holder.avatarInitialLetter.setVisibility(View.VISIBLE);
                }
            }
        }
//        ((ViewHolderNormalChatList)holder).contactInitialLetter.setTextSize(24);

    }
    //Group call: participant avatar
    public void setProfileParticipantAvatarGroupCall(long peerid, String fullName, ViewHolderGroupCall holder){
        log("setProfileParticipantAvatarGroupCall()");

        if(peerid == megaChatApi.getMyUserHandle()){
            //My peer
            String contactMail = megaChatApi.getMyEmail();

            File avatar = null;
            if (context != null) {
                if (context.getExternalCacheDir() != null) {
                    avatar = new File(context.getExternalCacheDir().getAbsolutePath(), contactMail + ".jpg");
                } else {
                    avatar = new File(context.getCacheDir().getAbsolutePath(), contactMail + ".jpg");
                }
            }

            Bitmap bitmap = null;
            if (avatar.exists()) {
                if (avatar.length() > 0) {
                    BitmapFactory.Options bOpts = new BitmapFactory.Options();
                    bOpts.inPurgeable = true;
                    bOpts.inInputShareable = true;
                    bitmap = BitmapFactory.decodeFile(avatar.getAbsolutePath(), bOpts);
//                    bitmap = ThumbnailUtilsLollipop.getRoundedRectBitmap(context, bitmap, 3);
                    if (bitmap != null) {
                        holder.avatarInitialLetter.setVisibility(GONE);
                        holder.avatarImage.setVisibility(View.VISIBLE);
                        holder.avatarImage.setImageBitmap(bitmap);
                    }else{
                        createDefaultParticipantAvatarGroupCall(peerid, holder, fullName, contactMail);
                    }
                }else {
                    createDefaultParticipantAvatarGroupCall(peerid, holder, fullName, contactMail);
                }
            }else {
                createDefaultParticipantAvatarGroupCall(peerid, holder, fullName, contactMail);
            }

        }else{
            //Contact
            String contactMail = megaChatApi.getContactEmail(peerid);
            if(contactMail == null){
                contactMail = " ";
            }
            createDefaultParticipantAvatarGroupCall(peerid, holder, fullName, contactMail);

            ChatUserAvatarListener listener = new ChatUserAvatarListener(context, holder, this);
            File avatar = null;

            if(contactMail == null){
                if (context.getExternalCacheDir() != null) {
                    avatar = new File(context.getExternalCacheDir().getAbsolutePath(), peerid + ".jpg");
                }else {
                    avatar = new File(context.getCacheDir().getAbsolutePath(), peerid + ".jpg");
                }
            }else{
                if (context.getExternalCacheDir() != null){
                    avatar = new File(context.getExternalCacheDir().getAbsolutePath(), contactMail + ".jpg");
                }else{
                    avatar = new File(context.getCacheDir().getAbsolutePath(), contactMail + ".jpg");
                }
            }
            Bitmap bitmap = null;
            if (avatar.exists()){
                if (avatar.length() > 0){
                    BitmapFactory.Options bOpts = new BitmapFactory.Options();
                    bOpts.inPurgeable = true;
                    bOpts.inInputShareable = true;
                    bitmap = BitmapFactory.decodeFile(avatar.getAbsolutePath(), bOpts);
                    if (bitmap == null) {
                        avatar.delete();
                        if(megaApi==null){
                            log("setUserAvatar: megaApi is Null in Offline mode");
                            return;
                        }
                        if (context.getExternalCacheDir() != null){
                            megaApi.getUserAvatar(contactMail, context.getExternalCacheDir().getAbsolutePath() + "/" + contactMail + ".jpg", listener);
                        }else{
                            megaApi.getUserAvatar(contactMail, context.getCacheDir().getAbsolutePath() + "/" + contactMail + ".jpg", listener);
                        }
                    }else{
                        holder.avatarInitialLetter.setVisibility(GONE);
                        holder.avatarImage.setVisibility(View.VISIBLE);
                        holder.avatarImage.setImageBitmap(bitmap);
                    }
                }else{

                    if(megaApi==null){
                        log("setUserAvatar: megaApi is Null in Offline mode");
                        return;
                    }
                    if (context.getExternalCacheDir() != null){
                        megaApi.getUserAvatar(contactMail, context.getExternalCacheDir().getAbsolutePath() + "/" + contactMail + ".jpg", listener);
                    }else{
                        megaApi.getUserAvatar(contactMail, context.getCacheDir().getAbsolutePath() + "/" + contactMail + ".jpg", listener);
                    }
                }
            }else{
                if(megaApi==null){
                    log("setUserAvatar: megaApi is Null in Offline mode");
                    return;
                }
                if (context.getExternalCacheDir() != null){
                    megaApi.getUserAvatar(contactMail, context.getExternalCacheDir().getAbsolutePath() + "/" + contactMail + ".jpg", listener);
                }else{
                    megaApi.getUserAvatar(contactMail, context.getCacheDir().getAbsolutePath() + "/" + contactMail + ".jpg", listener);
                }
            }
        }

    }

    public RecyclerView getListFragment() {
        return recyclerViewFragment;
    }

    public void setListFragment(RecyclerView recyclerViewFragment) {
        this.recyclerViewFragment = recyclerViewFragment;
    }

    public void changesInQuality(int position, ViewHolderGroupCall holder){

        if(holder == null){
            holder = (ViewHolderGroupCall) recyclerViewFragment.findViewHolderForAdapterPosition(position);
        }
        if(holder!=null){
            InfoPeerGroupCall peer = getNodeAt(position);
            if (peer == null){
                return;
            }

            if(peer.isGoodQuality()){
                holder.qualityLayout.setVisibility(View.GONE);
            }else{
                if(peer.isVideoOn()){
                    if(peers.size() < 7){
                        RelativeLayout.LayoutParams paramsQuality = new RelativeLayout.LayoutParams(holder.qualityIcon.getLayoutParams());
                        paramsQuality.height = Util.scaleWidthPx(24, outMetrics);
                        paramsQuality.width = Util.scaleWidthPx(24, outMetrics);
                        paramsQuality.setMargins(Util.scaleWidthPx(15, outMetrics), 0,  0, Util.scaleWidthPx(15, outMetrics));
                        paramsQuality.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE);
                        paramsQuality.addRule(RelativeLayout.ALIGN_PARENT_LEFT,  RelativeLayout.TRUE);
                        holder.qualityIcon.setLayoutParams(paramsQuality);
                    }else{
                        RelativeLayout.LayoutParams paramsQuality = new RelativeLayout.LayoutParams(holder.qualityIcon.getLayoutParams());
                        paramsQuality.height = Util.scaleWidthPx(20, outMetrics);
                        paramsQuality.width = Util.scaleWidthPx(20, outMetrics);
                        paramsQuality.setMargins(Util.scaleWidthPx(7, outMetrics), 0,  0, Util.scaleWidthPx(7, outMetrics));
                        paramsQuality.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE);
                        paramsQuality.addRule(RelativeLayout.ALIGN_PARENT_LEFT,  RelativeLayout.TRUE);
                        holder.qualityIcon.setLayoutParams(paramsQuality);
                    }
                    holder.qualityLayout.setVisibility(View.VISIBLE);
                }else{
                    holder.qualityLayout.setVisibility(View.GONE);
                }
            }
        }else{
            notifyItemChanged(position);
        }
    }

    public void changesInAudio(int position, ViewHolderGroupCall holder){
        log("changesInAudio");

        if(holder == null){
            holder = (ViewHolderGroupCall) recyclerViewFragment.findViewHolderForAdapterPosition(position);
        }
        if(holder!=null){
            InfoPeerGroupCall peer = getNodeAt(position);
            if (peer == null){
                return;
            }

            if(peer.isAudioOn()){
                holder.microAvatar.setVisibility(View.GONE);
                holder.microSurface.setVisibility(View.GONE);

            }else{
                if(!peer.isVideoOn()){
                    holder.microSurface.setVisibility(View.GONE);

                    holder.microAvatar.setVisibility(View.VISIBLE);
                    //Micro icon:
                    if(peers.size() < 7){
                        RelativeLayout.LayoutParams paramsMicroAvatar = new RelativeLayout.LayoutParams(holder.microAvatar.getLayoutParams());
                        paramsMicroAvatar.height = Util.scaleWidthPx(24, outMetrics);
                        paramsMicroAvatar.width = Util.scaleWidthPx(24, outMetrics);
                        paramsMicroAvatar.setMargins(Util.scaleWidthPx(10, outMetrics), 0, 0, 0);
                        paramsMicroAvatar.addRule(RelativeLayout.RIGHT_OF, R.id.avatar_rl);
                        paramsMicroAvatar.addRule(RelativeLayout.ALIGN_TOP, R.id.avatar_rl);
                        holder.microAvatar.setLayoutParams(paramsMicroAvatar);

                        ViewGroup.LayoutParams paramsAvatarImage = (ViewGroup.LayoutParams) holder.avatarImage.getLayoutParams();
                        paramsAvatarImage.width = Util.scaleWidthPx(88, outMetrics);
                        paramsAvatarImage.height = Util.scaleWidthPx(88, outMetrics);
                        holder.avatarImage.setLayoutParams(paramsAvatarImage);
                        holder.avatarInitialLetter.setTextSize(TypedValue.COMPLEX_UNIT_SP, 50f);
                    }else{
                        RelativeLayout.LayoutParams paramsMicroAvatar = new RelativeLayout.LayoutParams(holder.microAvatar.getLayoutParams());
                        paramsMicroAvatar.height = Util.scaleWidthPx(15, outMetrics);
                        paramsMicroAvatar.width = Util.scaleWidthPx(15, outMetrics);
                        paramsMicroAvatar.setMargins(0, 0, 0, 0);
                        paramsMicroAvatar.addRule(RelativeLayout.RIGHT_OF, R.id.avatar_rl);
                        paramsMicroAvatar.addRule(RelativeLayout.ALIGN_TOP, R.id.avatar_rl);
                        holder.microAvatar.setLayoutParams(paramsMicroAvatar);

                        ViewGroup.LayoutParams paramsAvatarImage = (ViewGroup.LayoutParams) holder.avatarImage.getLayoutParams();
                        paramsAvatarImage.width = Util.scaleWidthPx(60, outMetrics);
                        paramsAvatarImage.height = Util.scaleWidthPx(60, outMetrics);
                        holder.avatarImage.setLayoutParams(paramsAvatarImage);
                        holder.avatarInitialLetter.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30f);
                    }

                }else{
                    holder.microAvatar.setVisibility(View.GONE);
                    holder.microSurface.setVisibility(View.VISIBLE);

                    //Audio icon:
                    if(peers.size() < 7){
                        RelativeLayout.LayoutParams paramsMicroSurface = new RelativeLayout.LayoutParams(holder.microSurface.getLayoutParams());
                        paramsMicroSurface.height = Util.scaleWidthPx(24, outMetrics);
                        paramsMicroSurface.width = Util.scaleWidthPx(24, outMetrics);
                        paramsMicroSurface.setMargins(0, Util.scaleWidthPx(15, outMetrics),  Util.scaleWidthPx(15, outMetrics), 0);
                        paramsMicroSurface.addRule(RelativeLayout.ALIGN_TOP, R.id.parent_surface_view);
                        paramsMicroSurface.addRule(RelativeLayout.ALIGN_RIGHT, R.id.parent_surface_view);
                        holder.microSurface.setLayoutParams(paramsMicroSurface);
                    }else{
                        RelativeLayout.LayoutParams paramsMicroSurface = new RelativeLayout.LayoutParams(holder.microSurface.getLayoutParams());
                        paramsMicroSurface.height = Util.scaleWidthPx(15, outMetrics);
                        paramsMicroSurface.width = Util.scaleWidthPx(15, outMetrics);
                        paramsMicroSurface.setMargins(0,  Util.scaleWidthPx(7, outMetrics),  Util.scaleWidthPx(7, outMetrics), 0);
                        paramsMicroSurface.addRule(RelativeLayout.ALIGN_TOP, R.id.parent_surface_view);
                        paramsMicroSurface.addRule(RelativeLayout.ALIGN_RIGHT, R.id.parent_surface_view);
                        holder.microSurface.setLayoutParams(paramsMicroSurface);
                    }
                }
            }
        }else{
            notifyItemChanged(position);
        }
    }

    public void updateMode(boolean flag){
        isManualMode = flag;
    }

    public void changesInGreenLayer(int position, ViewHolderGroupCall holder){
        log("changesInGreenLayer()");
        if(holder == null){
            holder = (ViewHolderGroupCall) recyclerViewFragment.findViewHolderForAdapterPosition(position);
        }
        if(holder!=null){
            InfoPeerGroupCall peer = getNodeAt(position);
            if (peer == null){
                return;
            }
            if(peer.hasGreenLayer()){
                if(isManualMode){
                    holder.greenLayer.setBackground(ContextCompat.getDrawable(context, R.drawable.border_green_layer_selected));
                }else {
                    holder.greenLayer.setBackground(ContextCompat.getDrawable(context, R.drawable.border_green_layer));
                }
                holder.greenLayer.setVisibility(View.VISIBLE);
            }else{
                holder.greenLayer.setVisibility(View.GONE);
            }

        }else{
            notifyItemChanged(position);
        }
    }

    @Override
    public void resetSize(long peerid, long clientid) {
        log("resetSize");
        if(getItemCount()!=0){

           if((peers!=null)&&(peers.size()>0)){
               for(InfoPeerGroupCall peer:peers){
                   if(peer.getListener()!=null){
                       if(peer.getListener().getWidth()!=0){
                           peer.getListener().setWidth(0);
                       }
                       if(peer.getListener().getHeight()!=0){
                           peer.getListener().setHeight(0);
                       }
                   }
               }
           }

        }
    }

    public void onDestroy(){
        log("onDestroy()");
        ViewHolderGroupCall holder = null;
        if((peers!=null)&&(peers.size()>0)) {
            for(int i=0; i<peers.size(); i++){
                if(holder == null){
                    holder = (ViewHolderGroupCall) recyclerViewFragment.findViewHolderForAdapterPosition(i);
                }
                if(holder!=null){
                    log("onDestroy()  holder != null");
                    InfoPeerGroupCall peer = getNodeAt(i);
                    if (peer == null){
                        return;
                    }
                    //Remove SurfaceView && Listener:
                    if(peer.getListener() != null){
                        if((peer.getPeerId() == megaChatApi.getMyUserHandle()) && (peer.getClientId() == megaChatApi.getMyClientidHandle(chatId))){
                            megaChatApi.removeChatVideoListener(chatId, -1, -1, peer.getListener());
                        }else{
                            megaChatApi.removeChatVideoListener(chatId, peer.getPeerId(), peer.getClientId(), peer.getListener());
                        }
                        if(holder.parentSurfaceView.getChildCount() == 0){
                            if(peer.getListener().getSurfaceView().getParent()!=null){
                                if(peer.getListener().getSurfaceView().getParent().getParent()!=null){
                                    ((ViewGroup)peer.getListener().getSurfaceView().getParent()).removeView(peer.getListener().getSurfaceView());
                                }
                            }
                        }else{

                            holder.parentSurfaceView.removeAllViews();
                            holder.parentSurfaceView.removeAllViewsInLayout();
                            if(peer.getListener().getSurfaceView().getParent()!=null){
                                if(peer.getListener().getSurfaceView().getParent().getParent()!=null){
                                    ((ViewGroup)peer.getListener().getSurfaceView().getParent()).removeView(peer.getListener().getSurfaceView());
                                }
                            }
                        }
                        peer.getListener().getSurfaceView().setVisibility(GONE);
                        peer.setListener(null);
                    }

                }else{
                    log("onDestroy()  holder == null");

//                notifyItemChanged(i);
                }
        }

        }

    }

    public ArrayList<InfoPeerGroupCall> getPeers() {
        return peers;
    }

    public void setPeers(ArrayList<InfoPeerGroupCall> peers) {
        this.peers = peers;
    }

    private static void log(String log) {
        Util.log("GroupCallAdapter", log);
    }


}