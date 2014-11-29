package com.oops.wifichat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.oops.wifichat.ChatConnection.ConnectionListener;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.ActionListener;
import android.net.wifi.p2p.WifiP2pManager.ChannelListener;
import android.net.wifi.p2p.WifiP2pManager.ConnectionInfoListener;
import android.net.wifi.p2p.WifiP2pManager.DnsSdServiceResponseListener;
import android.net.wifi.p2p.WifiP2pManager.DnsSdTxtRecordListener;
import android.net.wifi.p2p.WifiP2pManager.PeerListListener;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

public class HomeActivity extends Activity implements DeviceActionListener, ConnectionListener {

	public static final int SERVER_PORT = 10086;
	protected static final String TAG = "HOME";
	private WifiP2pManager mManager;
	private WifiP2pManager.Channel channel;
	final HashMap<String, String> buddies = new HashMap<String, String>();
	private final IntentFilter intentFilter = new IntentFilter();

	private Handler mUpdateHandler;
	private ChatConnection mConnection;
	
	private DeviceListFragment listFragment;
	private DeviceDetailFragment detailFragment;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_home);
		
		listFragment = (DeviceListFragment) getFragmentManager().findFragmentById(R.id.frag_list);
		detailFragment = (DeviceDetailFragment) getFragmentManager().findFragmentById(R.id.frag_detail);
		
		mUpdateHandler = new Handler() {
            @Override
	        public void handleMessage(Message msg) {
            	
            }
		};

	    //  Indicates a change in the Wi-Fi P2P status.
	    intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);

	    // Indicates a change in the list of available peers.
	    intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);

	    // Indicates the state of Wi-Fi P2P connectivity has changed.
	    intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);

	    // Indicates this device's details have changed.
	    intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

		mManager = (WifiP2pManager) getSystemService(WIFI_P2P_SERVICE);
		channel = mManager.initialize(this, getMainLooper(), new ChannelListener() {
			
			@Override
			public void onChannelDisconnected() {
				// TODO Auto-generated method stub
				
			}
		});
		
		receiver = new WiFiDirectBroadcastReceiver();
        registerReceiver(receiver, intentFilter);
	}

	@Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.action_items, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        	case android.R.id.home:   // using app icon for navigation up or home:
        		Log.d(TAG, " navigating up or home clicked.");
        		// startActivity(new Intent(home.class, Intent.FLAG_ACTIVITY_CLEAR_TOP));
        		return true;
        		
            case R.id.atn_direct_enable:
                    // Since this is the system wireless settings activity, it's
                    // not going to send us a result. We will be notified by
                    // WiFiDeviceBroadcastReceiver instead.
                    startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS));
                return true;
            case R.id.atn_direct_discover:
                // show progressbar when discoverying.
                listFragment.onInitiateDiscovery();  
                Log.d(TAG, "onOptionsItemSelected : start discoverying ");
                discoverPeers();
                
                return true;
                
            case R.id.disconnect:
            	Log.d(TAG, "onOptionsItemSelected : disconnect all connections and stop server ");
            	mConnection.tearDown();
            	return true;
            	
            case R.id.about:
            	Log.d(TAG, "onOptionsItemSelected : about ");
            	Toast.makeText(this, "Free China using Peer-Peer", Toast.LENGTH_LONG).show();
            	return true;
            	
            case R.id.help:
            	Log.d(TAG, "onOptionsItemSelected : help ");
            	Toast.makeText(this, "learn to use Peer-Peer to fight against censorship", Toast.LENGTH_LONG).show();
            	return true;
            	
            default:
                return super.onOptionsItemSelected(item);
        }
    }
	
    public  void startRegistration() {
        //  Create a string map containing information about your service.
        HashMap<String, String> record = new HashMap<String, String>();
        record.put("listenport", String.valueOf(SERVER_PORT));
        record.put("buddyname", "John Doe" + (int) (Math.random() * 1000));
        record.put("available", "visible");

        // Service information.  Pass it an instance name, service type
        // _protocol._transportlayer , and the map containing
        // information other devices will want once they connect to this one.
        WifiP2pDnsSdServiceInfo serviceInfo =
                WifiP2pDnsSdServiceInfo.newInstance("_test", "_presence._tcp", record);

        // Add the local service, sending the service info, network channel,
        // and listener that will be used to indicate success or failure of
        // the request.
        mManager.addLocalService(channel, serviceInfo, new ActionListener() {
            @Override
            public void onSuccess() {
                // Command successful! Code isn't necessarily needed here,
                // Unless you want to update the UI or add logging statements.
            	Log.d(TAG, "Success");
            }

            @Override
            public void onFailure(int arg0) {
                // Command failed.  Check for P2P_UNSUPPORTED, ERROR, or BUSY
            	Log.d(TAG, "failure");
            }
        });
    }
    
    public void discoverService() {
        DnsSdTxtRecordListener txtListener = new DnsSdTxtRecordListener() {
            /* Callback includes:
             * fullDomain: full domain name: e.g "printer._ipp._tcp.local."
             * record: TXT record dta as a map of key/value pairs.
             * device: The device running the advertised service.
             */
			@Override
			public void onDnsSdTxtRecordAvailable(String fullDomain, Map<String, String> record, WifiP2pDevice device) {
				// TODO Auto-generated method stub
				Log.d(TAG, "DnsSdTxtRecord available -" + record.toString());
                buddies.put(device.deviceAddress, record.get("buddyname"));
			}
        };
        
        DnsSdServiceResponseListener servListener = new DnsSdServiceResponseListener() {
            @Override
            public void onDnsSdServiceAvailable(String instanceName, String registrationType,
                    WifiP2pDevice resourceType) {

                    // Update the device name with the human-friendly version from
                    // the DnsTxtRecord, assuming one arrived.
                    resourceType.deviceName = buddies
                            .containsKey(resourceType.deviceAddress) ? buddies
                            .get(resourceType.deviceAddress) : resourceType.deviceName;

                    // Add to the custom adapter defined specifically for showing
                    // wifi devices.
//                    WiFiDirectServicesList fragment = (WiFiDirectServicesList) getFragmentManager()
//                            .findFragmentById(R.id.frag_peerlist);
//                    WiFiDevicesAdapter adapter = ((WiFiDevicesAdapter) fragment
//                            .getListAdapter());
//
//                    adapter.add(resourceType);
//                    adapter.notifyDataSetChanged();
                     Log.d(TAG, "onBonjourServiceAvailable " + instanceName);
            }
        };
        
        mManager.setDnsSdResponseListeners(channel, servListener, txtListener);

        WifiP2pDnsSdServiceRequest serviceRequest = WifiP2pDnsSdServiceRequest.newInstance();
        mManager.addServiceRequest(channel,
                serviceRequest,
                new ActionListener() {
                    @Override
                    public void onSuccess() {
                        // Success!
                    	Log.d(TAG, "add request success");
                    }

                    @Override
                    public void onFailure(int code) {
                        // Command failed.  Check for P2P_UNSUPPORTED, ERROR, or BUSY
                    	Log.d(TAG, "add request fail");
                    }
                });
        
        mManager.discoverServices(channel, new ActionListener() {

            @Override
            public void onSuccess() {
                // Success!
            	Log.d(TAG, "Success");
            }

            @Override
            public void onFailure(int code) {
                // Command failed.  Check for P2P_UNSUPPORTED, ERROR, or BUSY
                if (code == WifiP2pManager.P2P_UNSUPPORTED) {
                    Log.d(TAG, "P2P isn't supported on this device.");
                }
                Log.d(TAG, "Failure");
            }
        });
        
    }
    
    private WiFiDirectBroadcastReceiver receiver;
    
    @Override
    protected void onResume() {
    	super.onResume();
    	
    	discoverPeers();
    }
    
    @Override
    protected void onPause() {
    	// TODO Auto-generated method stub
    	super.onPause();

    }
    
    @Override
    protected void onDestroy() {
    	// TODO Auto-generated method stub
    	super.onDestroy();
    	unregisterReceiver(receiver);    	
    	if(mConnection != null) {
    		mConnection.unregisterListener(this);
    		mConnection.tearDown();
    	}
    }
    
    private void discoverPeers() {
    	mManager.discoverPeers(channel, new WifiP2pManager.ActionListener() {

            @Override
            public void onSuccess() {
                // Code for when the discovery initiation is successful goes here.
                // No services have actually been discovered yet, so this method
                // can often be left blank.  Code for peer discovery goes in the
                // onReceive method, detailed below.
            	Toast.makeText(HomeActivity.this, "Discovery Initiated", Toast.LENGTH_SHORT).show();
                Log.d(TAG, "onOptionsItemSelected : discovery succeed... " );
            }

            @Override
            public void onFailure(int reasonCode) {
                // Code for when the discovery initiation fails goes here.
                // Alert the user that something went wrong.\
            	listFragment.clearPeers();
            	Toast.makeText(HomeActivity.this, "Discovery Failed, try again... ", Toast.LENGTH_SHORT).show();
            }
    });
    }
    
    private List peers = new ArrayList();
    private PeerListListener peerListListener = new PeerListListener() {
        @Override
        public void onPeersAvailable(WifiP2pDeviceList peerList) {

            // Out with the old, in with the new.
            peers.clear();
            peers.addAll(peerList.getDeviceList());
            listFragment.onPeersAvailable(peers);
        }
    };
    
    private ConnectionInfoListener connectionListener = new ConnectionInfoListener() {
		
		@Override
		public void onConnectionInfoAvailable(WifiP2pInfo info) {
			// TODO Auto-generated method stub
			// InetAddress from WifiP2pInfo struct.
			
	        // After the group negotiation, we can determine the group owner.
			if(info.groupFormed) {
				if(mConnection != null) {
					mConnection.unregisterListener(HomeActivity.this);
				}
				mConnection = new ChatConnection(mUpdateHandler, HomeActivity.this);
	    	    ChatApplication app = (ChatApplication) getApplication();
	    	    app.connection = mConnection;
	    	    mConnection.registerListener(HomeActivity.this);
	    	    
	    	    if (info.groupFormed && info.isGroupOwner) {
		            // Do whatever tasks are specific to the group owner.
		            // One common case is creating a server thread and accepting
		            // incoming connections.
		        } else if (info.groupFormed) {
		            // The other device acts as the client. In this case,
		            // you'll want to create a client thread that connects to the group
		            // owner.
		        	mConnection.connectToServer(info.groupOwnerAddress, SERVER_PORT);
		        }
			}
		}
	};
    
    public void connect() {
        // Picking the first device found on the network.
    	if(peers.size() == 0) {
    		return;
    	}
    	
        WifiP2pDevice device = (WifiP2pDevice) peers.get(0);

        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = device.deviceAddress;
        config.wps.setup = WpsInfo.PBC;

        mManager.connect(channel, config, new ActionListener() {

            @Override
            public void onSuccess() {
                // WiFiDirectBroadcastReceiver will notify us. Ignore for now.
            }

            @Override
            public void onFailure(int reason) {
                Toast.makeText(HomeActivity.this, "Connect failed. Retry.",
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    
    class WiFiDirectBroadcastReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
	        if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
	            // Determine if Wifi P2P mode is enabled or not, alert
	            // the Activity.
	            int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
	            if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
//	                activity.setIsWifiP2pEnabled(true);
//	            } else {
//	                activity.setIsWifiP2pEnabled(false);
	            }
	        } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {

	            // The peer list has changed!  We should probably do something about
	            // that.
	        	
	            // Request available peers from the wifi p2p manager. This is an
	            // asynchronous call and the calling activity is notified with a
	            // callback on PeerListListener.onPeersAvailable()
	            if (mManager != null) {
	                mManager.requestPeers(channel, peerListListener);
	            }
	            Log.d(TAG, "P2P peers changed");

	        } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {

	            // Connection state changed!  We should probably do something about
	            // that.
	        	if (mManager == null) {
	                return;
	            }

	            NetworkInfo networkInfo = (NetworkInfo) intent
	                    .getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);

	            if (networkInfo.isConnected()) {

	                // We are connected with the other device, request connection
	                // info to find group owner IP

	                mManager.requestConnectionInfo(channel, connectionListener);
	            }

	        } else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {
	            listFragment.updateThisDevice((WifiP2pDevice) intent.getParcelableExtra(
	                    WifiP2pManager.EXTRA_WIFI_P2P_DEVICE));
	        }
		}
    }


	@Override
	public void showDetails(WifiP2pDevice device) {
		// TODO Auto-generated method stub
		detailFragment.showDetails(device);
	}

	@Override
	public void cancelDisconnect() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void connect(WifiP2pConfig config) {
		// TODO Auto-generated method stub
        mManager.connect(channel, config, new ActionListener() {

            @Override
            public void onSuccess() {
                // WiFiDirectBroadcastReceiver will notify us. Ignore for now.
            }

            @Override
            public void onFailure(int reason) {
                Toast.makeText(HomeActivity.this, "Connect failed. Retry.",
                        Toast.LENGTH_SHORT).show();
            }
        });
	}

	@Override
	public void disconnect() {
		// TODO Auto-generated method stub
		runOnUiThread(new Runnable() {
			
			@Override
			public void run() {
				detailFragment.resetViews();
			}
		});
		
		mConnection.tearDown();
		mManager.removeGroup(channel, new ActionListener() {
			
			@Override
			public void onSuccess() {
				// TODO Auto-generated method stub
				
			}
			
			@Override
			public void onFailure(int paramInt) {
				// TODO Auto-generated method stub
				detailFragment.getView().setVisibility(View.GONE);
			}
		});
	}

	@Override
	public void onConnectionReady() {
		Intent intent = new Intent(this, ChatActivity.class);
    	this.startActivity(intent);
	}

	@Override
	public void onConnectionDown() {
		disconnect();
	}
}
