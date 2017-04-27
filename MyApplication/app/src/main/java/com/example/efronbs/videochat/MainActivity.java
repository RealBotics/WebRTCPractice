package com.example.efronbs.videochat;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import com.neovisionaries.ws.client.WebSocket;
import com.neovisionaries.ws.client.WebSocketAdapter;
import com.neovisionaries.ws.client.WebSocketException;
import com.neovisionaries.ws.client.WebSocketExtension;
import com.neovisionaries.ws.client.WebSocketFactory;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.DataChannel;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.Logging;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RendererCommon;
import org.webrtc.RtpReceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoRenderer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    Context ctx;
    VideoRenderer.Callbacks remoteRender;
    ProxyRenderer remoteProxyRenderer = new ProxyRenderer();
    SurfaceViewRenderer svr; // I have no idea wtf to call this
    WebSocket ws = null;
    EglBase rootEGLBase;

//    final String VIDEO_TRACK_ID = "MyVideoTrack";
//    final String AUDIO_TRACK_ID = "MyAudioTrack";

    private class ProxyRenderer implements VideoRenderer.Callbacks {
        private VideoRenderer.Callbacks target;
        synchronized public void renderFrame(VideoRenderer.I420Frame frame) {
            if (target == null) {
                VideoRenderer.renderFrameDone(frame);
                return;
            }
            target.renderFrame(frame);
        }
        synchronized public void setTarget(VideoRenderer.Callbacks target) {
            this.target = target;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        System.out.println("CREATED2");

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ctx = getApplicationContext();

        new ServerConnection().execute();

        // ICE servers
        PeerConnection.IceServer iceServer = new PeerConnection.IceServer("http://stun.stun.l.google.com:19302/");
        List<PeerConnection.IceServer> servers = new ArrayList<PeerConnection.IceServer>();
        servers.add(iceServer);

        // Media Constraints
        final MediaConstraints mediaConstraints = new MediaConstraints();
        mediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        mediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));

        // Set up remote rendering stuff
        rootEGLBase = EglBase.create();
        svr = (SurfaceViewRenderer) findViewById(R.id.remote_video);
        svr.init(rootEGLBase.getEglBaseContext(), null);
        svr.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
        svr.setZOrderMediaOverlay(true);
        svr.setEnableHardwareScaler(true);

        remoteProxyRenderer.setTarget(svr);
        remoteRender = remoteProxyRenderer;
        
        // still need to attach this to VideoRenderer Callback

//        GLSurfaceView videoView = (GLSurfaceView) findViewById(R.id.remote_video);
//        VideoRendererGui.setView(videoView, null);
//        remoteRender = VideoRendererGui.create(0, 0, 100, 100, RendererCommon.ScalingType.SCALE_ASPECT_FILL, false);

        // Create a peerconnection factory
        PeerConnectionFactory.initializeAndroidGlobals(this.ctx, true);
        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        PeerConnectionFactory peerConnectionFactory = new PeerConnectionFactory(options);

        // wait until my websocket has connected
        System.out.println("waiting for websocket to be inited...");
        while (this.ws == null);
        System.out.println("done wait");
        final WebSocket in_scope_ws = this.ws;

        // Create peerconnection
        final PeerConnection peerConnection = peerConnectionFactory.createPeerConnection(servers, mediaConstraints, new PeerConnection.Observer() {


            @Override
            public void onSignalingChange(PeerConnection.SignalingState signalingState) {

            }

            @Override
            public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {

            }

            @Override
            public void onIceConnectionReceivingChange(boolean b) {

            }

            @Override
            public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {

            }

            // not sure what to put here
            @Override
            public void onIceCandidate(IceCandidate iceCandidate) {
                if (iceCandidate != null && in_scope_ws != null) {
                    try {
                        JSONObject jsonObject = new JSONObject();
                        jsonObject.put("ice", iceCandidate.toString());
                        in_scope_ws.sendText(jsonObject.toString());
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    //send ice candidate to server - probably need to convert to string and put in JSON object
//                    in_scope_ws.send("")
                }
            }

            @Override
            public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {

            }

            // pretty sure this is the only garbage I need right now
            @Override
            public void onAddStream(MediaStream mediaStream) {
                System.out.println("IN ADD STREAM");
                if(mediaStream.videoTracks.size()==0) {
                    Log.d("onAddStream", "NO REMOTE STREAM");
                    System.out.println("NO REMOTE STREAM (PRINTLN)");
                }
                mediaStream.videoTracks.get(0).addRenderer(new VideoRenderer (remoteRender));
//                VideoRendererGui.update(remoteRender, 0, 0, 100, 100, RendererCommon.ScalingType.SCALE_ASPECT_FILL, false);
            }

            @Override
            public void onRemoveStream(MediaStream mediaStream) {

            }

            @Override
            public void onDataChannel(DataChannel dataChannel) {

            }

            @Override
            public void onRenegotiationNeeded() {

            }

            @Override
            public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {

            }
        });

//        System.out.println(this.ws);
        System.out.println("ABOUT TO ADD NEW LISTENER");

        this.ws.addListener(new WebSocketAdapter() {
            @Override
            public void onTextMessage(WebSocket websocket, String message) throws Exception {
                System.out.println("RECEIVED MESSAGE FROM SERVER");
                JSONObject receivedJson = new JSONObject(message);
                if (receivedJson.has("ice")) {
                    System.out.println("ADDING ICE CANDIDATE");

                    JSONObject newCandidateInfo = receivedJson.getJSONObject("ice");
                    IceCandidate newCandidate = new IceCandidate(
                            newCandidateInfo.getString("sdpMid"),
                            newCandidateInfo.getInt("sdpMLineIndex"),
                            newCandidateInfo.getString("candidate")
                    );
                    peerConnection.addIceCandidate(newCandidate);
                }
                if (receivedJson.has("sdp")) {
                    System.out.println("ADDING SESSION DESCRIPTION");

                    String sdpStr = receivedJson.getJSONObject("sdp").getString("sdp");

//                    System.out.println(sdpStr);

                    SessionDescription sdp = new SessionDescription(SessionDescription.Type.OFFER, sdpStr);
                    System.out.println("NEW SDP");
                    System.out.println(sdp);
                    peerConnection.setRemoteDescription(new SdpObserver() {

                        String TAG = "SetRemoteDescription";

                        @Override
                        public void onCreateSuccess(SessionDescription sessionDescription) {
                            Logging.d(TAG, "SUCCESSFULLY CREATED remote description");
                            Log.d(TAG, "SUCCESSFULLY CREATED remote description");
                            System.out.println("SUCCESSFULLY CREATED remote description");
                        }

                        @Override
                        public void onSetSuccess() {
                            Logging.d(TAG, "SUCCESSFULLY SET remote description");
                            Log.d(TAG, "SUCCESSFULLY SET remote description");
                            System.out.println("SUCCESSFULLY SET remote description");
                            // create the answer to the session description
                            peerConnection.createAnswer(new SdpObserver() {
                                @Override
                                public void onCreateSuccess(SessionDescription sessionDescription) {
                                    System.out.println("SUCCESSFULLY CREATED ANSWER");
                                    // set the answer to our local session description
                                    peerConnection.setLocalDescription(new SdpObserver() {
                                        @Override
                                        public void onCreateSuccess(SessionDescription sessionDescription) {

                                        }

                                        @Override
                                        public void onSetSuccess() {
                                            System.out.println("SUCCESSFULLY SET local session description");
                                        }

                                        @Override
                                        public void onCreateFailure(String s) {

                                        }

                                        @Override
                                        public void onSetFailure(String s) {
                                            System.out.println("FAILED to SET local session description");
                                        }
                                    }, sessionDescription);

                                    // package the response and send it to the server
                                    try {
                                        JSONObject jsonObject = new JSONObject();
                                        jsonObject.put("sdp", sessionDescription.toString());
                                        in_scope_ws.sendText(jsonObject.toString());

                                    } catch (JSONException e) {
                                        e.printStackTrace();
                                    }
                                }

                                @Override
                                public void onSetSuccess() {

                                }

                                @Override
                                public void onCreateFailure(String s) {
                                    System.out.println("FAILED to CREATED ANSWER");
                                }

                                @Override
                                public void onSetFailure(String s) {

                                }
                            }, mediaConstraints);
                        }

                        @Override
                        public void onCreateFailure(String s) {
                            Logging.d(TAG, "Failed to CREATE remote description");
                            Log.e(TAG, "Failed to CREATE remote description");
                            System.out.println("Failed to CREATE remote description");
                        }

                        @Override
                        public void onSetFailure(String s) {
                            Logging.d(TAG, "Failed to SET remote description");
                            Log.e(TAG, "Failed to SET remote description");
                            System.out.println("Failed to SET remote description");
                        }
                    }, sdp);
                }
            }
        });

        while (true);
//        System.out.println("PASSED PERMISSION REQUEST");
    }

    public void doCameraShit(){
        // Create a video capture
//        String name = VideoCapturerAndroid.getNameOfFrontFacingDevice();
//        VideoCapturerAndroid capture = VideoCapturerAndroid.create(name);

//        // Create a peerconnection factory
//        PeerConnectionFactory.initializeAndroidGlobals(this.ctx , true, true, true, true);
//        PeerConnectionFactory peerConnectionFactory = new PeerConnectionFactory();
//
//        // Create media constraints for audio & video
//        MediaConstraints videoConstraints = new MediaConstraints();
//        MediaConstraints audioConstraints = new MediaConstraints();
//
//        // Create audio and video sources & tracks
//        VideoSource videoSource = peerConnectionFactory.createVideoSource(capture, videoConstraints);
//        VideoTrack localVideoTrack =peerConnectionFactory.createVideoTrack(VIDEO_TRACK_ID, videoSource);
//
//        AudioSource audioSource = peerConnectionFactory.createAudioSource(audioConstraints);
//        AudioTrack localAudioTrack = peerConnectionFactory.createAudioTrack(AUDIO_TRACK_ID, audioSource);
//
//        // Create video renderer
//        GLSurfaceView videoView = (GLSurfaceView) findViewById(R.id.gl_surface_view);
//
//        // I don't know what this runnable garbage is
//        VideoRendererGui.setView(videoView, new Runnable() {
//            @Override
//            public void run() {
//                Log.d("run", "VideoRendererGUI callback");
//            }
//        });
//
//        // Now that VideoRendererGui is ready, we can get our VideoRenderer
//        VideoRenderer renderer = null;
//        try {
//            renderer = VideoRendererGui.createGui(0, 0, 100, 100, VideoRendererGui.ScalingType.SCALE_ASPECT_FIT, true);
//            // And finally, with our VideoRenderer ready, we
//            // can add our renderer to the VideoTrack.
//            localVideoTrack.addRenderer(renderer);
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
    }

    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    public native String stringFromJNI();

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {

        System.out.println("IN PERMISSIONS CALLBACK");

        // If request is cancelled, the result arrays are empty.
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            System.out.println("CAMERA PERMISSIONS: " + (ContextCompat.checkSelfPermission(this.ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED));
            doCameraShit();
        }
    }

    public void setGlobalWebsocket(WebSocket result) {
        System.out.println("SETTING GLOBAL WEBSOCKET");
        this.ws = result;
    }

    private class ServerConnection extends AsyncTask<Void, Void, WebSocket>{

        @Override
        protected WebSocket doInBackground(Void ... params) {
            System.out.println("IN ASYNC TASK");

//            String ip = "ws://174.97.247.219:3434/";
            String ip = "ws://10.0.2.2:3434/";

            com.neovisionaries.ws.client.WebSocket ws = null;
            try {
                ws = new WebSocketFactory().createSocket(ip);
            } catch (IOException e) {
                e.printStackTrace();
            }

            ws.addListener(new WebSocketAdapter() {

                @Override

                public void onConnected(com.neovisionaries.ws.client.WebSocket websocket, Map<String, List<String>> headers) throws Exception {
                    System.out.println("CONNECTED! WOOHOO!");
                }

                @Override
                public void onTextMessage(com.neovisionaries.ws.client.WebSocket websocket, String text) throws Exception {
//                    System.out.println("RECEIVED MESSAGE TEXT");
//                    System.out.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
//                    System.out.println(text);
////                    JSONObject msg = new JSONObject(text);
////                    System.out.println(msg.get("ice"));
//                    System.out.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                }
            });

            try {
                ws.addExtension(WebSocketExtension.PERMESSAGE_DEFLATE);
                ws.connect();
            } catch (WebSocketException e) {
                e.printStackTrace();
            }

            System.out.println("RETURNING FROM DO IN BACKGROUND");
            setGlobalWebsocket(ws);
            return ws;

        }

//        @Override
//        protected void onPostExecute(WebSocket result) {
//            System.out.println("IN POST EXECUTE");
//            setGlobalWebsocket(result);
//        }
    }

}
