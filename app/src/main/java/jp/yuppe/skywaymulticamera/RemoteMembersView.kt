package jp.yuppe.skywaymulticamera

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.MutableLiveData
import com.ntt.skyway.core.content.remote.RemoteVideoStream
import jp.yuppe.skywaymulticamera.ui.theme.SkyWayMultiCameraTheme
import kotlin.math.ceil
import kotlin.math.sqrt

@Composable
fun RemoteMembersView(
    remoteVideoStreamList: MutableLiveData<MutableList<RemoteVideoStream>>?,
    modifier: Modifier = Modifier
) {
    val isInEditMode = LocalInspectionMode.current

    Box(modifier = modifier) {
        BoxWithConstraints {
            // メンバー数
            var memberCount = 0
            if (isInEditMode) {
                memberCount = 9 // レイアウト確認用
            } else {
                if (remoteVideoStreamList?.value != null) {
                    memberCount = remoteVideoStreamList.value!!.size
                }
            }

            // グリッドの桁数と行数
            var col = 1
            var row = 1
            if (memberCount > 0) {
                col = ceil(sqrt(memberCount.toDouble())).toInt() // 少数部切り上げ
                row = ceil(memberCount.toDouble() / col.toDouble()).toInt() // 少数部切り上げ
            }
            val gridWidth = maxWidth / col
            val gridHeight = maxHeight / row
            val padding = 2.dp

            LazyVerticalGrid(
                columns = GridCells.Fixed(col),
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center
            ) {
                items(memberCount) { index ->
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .width(gridWidth - padding)
                            .height(gridHeight - padding)
                            .padding(padding)
                            .background(Color.LightGray)
                    ) {
                        if (isInEditMode) {
                            // レイアウト確認用
                            RoomMemberItemViewDebug((index+1).toString())
                        } else {
                            val videoStream = remoteVideoStreamList?.value?.get(index)
                            videoStream?.let {
                                SkyWayRemoteVideoView(videoStream)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RoomMemberItemViewDebug(
    id: String,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(Color.LightGray)
        ) {
            Text(
                text = id,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleLarge,
            )
        }

        Text(
            text = "member$id",
            color = MaterialTheme.colorScheme.secondary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .align(alignment = Alignment.BottomStart)
                .padding(4.dp)
                .padding(2.dp)

        )
    }
}

@Preview(showBackground = true)
@Composable
fun SpeakerVideoViewPreview() {
    SkyWayMultiCameraTheme {
        RemoteMembersView(
            remoteVideoStreamList = null,
            modifier = Modifier.fillMaxSize()
        )
    }
}