package jp.yuppe.skywaymulticamera

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.MutableLiveData
import com.ntt.skyway.core.content.local.LocalVideoStream
import com.ntt.skyway.core.content.remote.RemoteVideoStream

@Composable
fun MultiVideoView(
    localVideoStream: LocalVideoStream?,
    remoteVideoStreamList: MutableLiveData<MutableList<RemoteVideoStream>>,
    modifier: Modifier = Modifier
) {
    val isPortrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT

    Box(modifier = modifier) {
        if (isPortrait) {
            Column {
                SkyWayLocalVideoView(
                    localVideoStream,
                    modifier = Modifier.weight(1f)
                )
                RemoteMembersView(
                    remoteVideoStreamList = remoteVideoStreamList,
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            Row {
                SkyWayLocalVideoView(
                    localVideoStream,
                    modifier = Modifier.weight(1f)
                )
                RemoteMembersView(
                    remoteVideoStreamList = remoteVideoStreamList,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
