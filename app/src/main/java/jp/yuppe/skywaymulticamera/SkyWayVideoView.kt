package jp.yuppe.skywaymulticamera

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.ntt.skyway.core.content.local.LocalVideoStream
import com.ntt.skyway.core.content.remote.RemoteVideoStream
import com.ntt.skyway.core.content.sink.SurfaceViewRenderer

@Composable
fun SkyWayLocalVideoView(localVideoStream: LocalVideoStream?, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { context ->
            val remoteVideoRenderer = SurfaceViewRenderer(context)
            remoteVideoRenderer.setup()
            remoteVideoRenderer
        },
        update = { surfaceViewRenderer ->
            localVideoStream?.removeAllRenderer()
            localVideoStream?.addRenderer(surfaceViewRenderer)
        },
        modifier = modifier
    )
}

@Composable
fun SkyWayRemoteVideoView(remoteVideoStream: RemoteVideoStream?, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { context ->
            val remoteVideoRenderer = SurfaceViewRenderer(context)
            remoteVideoRenderer.setup()
            remoteVideoRenderer
        },
        update = { surfaceViewRenderer ->
            remoteVideoStream?.removeAllRenderer()
            remoteVideoStream?.addRenderer(surfaceViewRenderer)
        },
        modifier = modifier
    )
}
