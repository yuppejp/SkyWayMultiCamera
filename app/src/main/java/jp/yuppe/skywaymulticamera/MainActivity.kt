package jp.yuppe.skywaymulticamera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.lifecycle.MutableLiveData
import com.ntt.skyway.core.SkyWayContext
import com.ntt.skyway.core.content.Stream
import com.ntt.skyway.core.content.local.LocalAudioStream
import com.ntt.skyway.core.content.local.LocalVideoStream
import com.ntt.skyway.core.content.local.source.AudioSource
import com.ntt.skyway.core.content.local.source.CameraSource
import com.ntt.skyway.core.content.local.source.CustomVideoFrameSource
import com.ntt.skyway.core.content.remote.RemoteVideoStream
import com.ntt.skyway.core.util.Logger
import com.ntt.skyway.room.RoomPublication
import com.ntt.skyway.room.member.LocalRoomMember
import com.ntt.skyway.room.member.RoomMember
import com.ntt.skyway.room.p2p.P2PRoom
import jp.yuppe.skywaymulticamera.ui.theme.SkyWayMultiCameraTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.UUID
import android.view.Surface

private const val token = YOUR_TOKEN

@Suppress("DEPRECATION")
class MainActivity : ComponentActivity() {
    private val roomName = "room1"
    private val memberName = UUID.randomUUID().toString()
    private val scope = CoroutineScope(Dispatchers.IO)
    private var room: P2PRoom? = null
    private var localRoomMember: LocalRoomMember? = null
    private var localVideoStream: LocalVideoStream? = null
    private var localAudioStream: LocalAudioStream? = null

    private var isJoined = mutableStateOf(false)
    private var isMultiCamera = mutableStateOf(false)
    private var remoteVideoStreamList = MutableLiveData<MutableList<RemoteVideoStream>>(mutableStateListOf())

    class StreamHolder(
        var publicationId: String,
        var videoStreamId: String = ""
    )

    private var streamHolders = MutableLiveData<MutableList<StreamHolder>>(mutableStateListOf())


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 権限の要求
        if (ContextCompat.checkSelfPermission(
                applicationContext, Manifest.permission.CAMERA
            ) != PermissionChecker.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                applicationContext, Manifest.permission.RECORD_AUDIO
            ) != PermissionChecker.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO
                ), 0
            )
        }

        setupSkyWay()

        setContent {
            SkyWayMultiCameraTheme {
                // A surface container using the 'background' color from the theme
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(roomName, modifier = Modifier.padding(horizontal = 8.dp))
                            Spacer(modifier = Modifier.weight(1f))
                            ToggleSwitch("Multi Camera", isMultiCamera.value) {
                                isMultiCamera.value = it
                                setupSkyWay()
                            }
                            Button(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                onClick = {
                                    if (isJoined.value) {
                                        leave()
                                    } else {
                                        join(roomName, memberName)
                                    }
                                }) {
                                if (isJoined.value) {
                                    Text("Leave")
                                } else {
                                    Text("Join")
                                }
                            }
                        }
                        MultiVideoView(localVideoStream, remoteVideoStreamList)
                    }
                }
            }
        }
    }

    private fun setupSkyWay() {
        if (isJoined.value) {
            leave()
        }

        runBlocking {
            scope.launch {
                val option = SkyWayContext.Options(
                    authToken = token,
                    logLevel = Logger.LogLevel.VERBOSE
                )

                val result = SkyWayContext.setup(applicationContext, option)
                if (!result) {
                    Log.d("*** setupSkyWay", "SkyWayContext.setup: failed")
                    return@launch
                }

                localVideoStream = if (isMultiCamera.value) {
                    setupCustomFrameSource()
                } else {
                    setupCamera(applicationContext)
                }

                localAudioStream = setupAudio()

                Log.d("*** setupSkyWay", "setupSkyWay: successful")
            }.join()
        }
    }

    private fun join(roomName: String, memberName: String) {
        scope.launch {
            room = P2PRoom.findOrCreate(name = roomName)
            if (room == null) {
                return@launch
            }

            room?.apply {
                onStreamPublishedHandler = { publication ->
                    Log.d("***", "onStreamPublishedHandler id: ${publication.id}, contentType: ${publication.contentType}")
                    if (publication.publisher?.id != localRoomMember?.id) {
                        subscribe(publication)
                    }
                }

                onStreamUnpublishedHandler = { publication ->
                    Log.d("***", "onStreamUnpublishedHandler id: ${publication.id}, contentType: ${publication.contentType}")
                    if (publication.publisher?.id != localRoomMember?.id) {
                        unsubscribe(publication)
                    }
                }

                onMemberJoinedHandler = { roomMember ->
                    Log.d("***", "onMemberJoinedHandler id: ${roomMember.id}, name: ${roomMember.name}")
                    if (roomMember.id == localRoomMember?.id) {
                        isJoined.value = true
                    }
                }

                onMemberLeftHandler = { roomMember ->
                    Log.d("***", "onMemberLeftHandler id: ${roomMember.id}, name: ${roomMember.name}")
                    if (roomMember.id == localRoomMember?.id) {
                        isJoined.value = false
                    }
                }
            }

            val member = RoomMember.Init(name = memberName)
            localRoomMember = room?.join(member)
            if (localRoomMember == null) {
                return@launch
            }

            localVideoStream?.let {
                localRoomMember?.publish(it)
            }
            localAudioStream?.let {
                localRoomMember?.publish(it)
            }

            isJoined.value = true
        }
    }

    private fun leave() {
        scope.launch {
            val task = async {
                localRoomMember?.leave()
                room?.dispose()
                room = null
            }
            task.await() // taskの終了待ち

            localRoomMember = null
            remoteVideoStreamList.value?.clear()
            streamHolders.value?.clear()
            isJoined.value = false
        }
    }

    private fun subscribe(publication: RoomPublication) {
        scope.launch {
            val subscription = localRoomMember?.subscribe(publication)
            val remoteStream = subscription?.stream
            Log.d("***", "subscribe contentType: ${remoteStream?.contentType}, publication.id: ${publication.id}")
            when (remoteStream?.contentType) {
                Stream.ContentType.VIDEO -> {
                    //remoteVideoStream.value = remoteStream as RemoteVideoStream?
                    remoteVideoStreamList.value?.add(remoteStream as RemoteVideoStream)

                    val streamHolder = StreamHolder(publicationId = publication.id, videoStreamId = remoteStream.id)
                    streamHolders.value?.add(streamHolder)
                }

                else -> {
                    // Handle other stream types
                }
            }
        }
    }

    private fun unsubscribe(publication: RoomPublication) {
        Log.d("***", "unsubscribe publication.id: ${publication.id}")
        scope.launch {
            val streamHolder = streamHolders.value?.find { it.publicationId == publication.id }
            streamHolder?.let {
                val item = remoteVideoStreamList.value?.find { it.id == streamHolder.videoStreamId }
                item?.let {
                    val result = remoteVideoStreamList.value?.remove(it)
                    Log.d("***", "unsubscribe@remove result: $result")
                }
            }
        }
    }

    private fun setupCamera(context: Context): LocalVideoStream {
        val device = CameraSource.getFrontCameras(context).first()
        val cameraOption = CameraSource.CapturingOptions(800, 800)
        CameraSource.startCapturing(context, device, cameraOption)
        return CameraSource.createStream()
    }

    private fun setupAudio(): LocalAudioStream {
        AudioSource.start()
        return AudioSource.createStream()
    }

    @SuppressLint("MissingPermission")
    private fun setupCustomFrameSource(): LocalVideoStream {
        class SyncedFrameData {
            var frontBitmap: Bitmap? = null
            var backBitmap: Bitmap? = null

            fun isAvailable(): Boolean {
                return frontBitmap != null && backBitmap != null
            }
        }

        val sourceSize = Size(1920 / 2, 1080 / 2)
        val source = CustomVideoFrameSource(sourceSize.width, sourceSize.height)
        val localVideoStream = source.createStream()

        // Camera2 APIの初期化
        val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val frontCameraId = getCameraId(manager, CameraCharacteristics.LENS_FACING_FRONT)
        val backCameraId = getCameraId(manager, CameraCharacteristics.LENS_FACING_BACK)

        // カメラセンサーの向き
        var frontCameraRatation = 0
        var backCameraRatation = 0
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        //Log.d("***", "windowManager.defaultDisplay.rotation: $windowManager.defaultDisplay.rotation")

        // 新しいバックグラウンドスレッドを作成し、Looperを関連付ける
        val handlerThread = HandlerThread("CameraBackground").also { it.start() }
        val handler = Handler(handlerThread.looper)

        // 二つのカメラを同期して処理するためのオブジェクト
        val frameData = SyncedFrameData()

        // フロントカメラ
        frontCameraId?.let { cameraId ->
            val imageReader = ImageReader.newInstance(sourceSize.width, sourceSize.height, ImageFormat.JPEG, 2)

            manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    val builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                    builder.addTarget(imageReader.surface)
                    camera.createCaptureSession(listOf(imageReader.surface), object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            session.setRepeatingRequest(builder.build(), null, handler)
                        }

                        override fun onConfigureFailed(session: CameraCaptureSession) {}
                    }, null)

                    frontCameraRatation = getCameraRotation(manager, cameraId)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                }
            }, handler)

            // ImageReaderのlistenerを設定
            imageReader.setOnImageAvailableListener({
                val image = it.acquireLatestImage()

                if (image != null && image.planes != null && image.planes.isNotEmpty()) {
                    synchronized(frameData) {
                        val buffer = image.planes[0].buffer
                        val bytes = ByteArray(buffer.remaining())
                        buffer.get(bytes)
                        frameData.frontBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        image.close()

                        // カメラセンサーの向きと端末の向きに応じてイメージの向きを決定
                        var frontRation = 0
                        var backRation = 0
                        when (windowManager.defaultDisplay.rotation) {
                            Surface.ROTATION_0 -> {
                                frontRation = frontCameraRatation
                                backRation = backCameraRatation
                            }

                            Surface.ROTATION_90 -> {
                                frontRation = frontCameraRatation - 90
                                backRation = backCameraRatation + 90
                            }

                            Surface.ROTATION_270 -> {
                                frontRation = 0
                                backRation = 0
                            }

                            else -> {}
                        }

                        // イメージ合成
                        if (frameData.isAvailable()) {
                            val combinedBitmap =
                                combineImages(frameData.backBitmap!!, frameData.frontBitmap!!, frontRation, backRation)
                            source.updateFrame(combinedBitmap, 0)
                            //Thread.sleep(500) // 負荷低減
                        }
                    }
                }
            }, handler)
        }

        // バックカメラ
        backCameraId?.let { cameraId ->
            val imageReader = ImageReader.newInstance(sourceSize.width, sourceSize.height, ImageFormat.JPEG, 2)

            manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    val builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                    builder.addTarget(imageReader.surface)
                    camera.createCaptureSession(listOf(imageReader.surface), object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            session.setRepeatingRequest(builder.build(), null, handler)
                        }

                        override fun onConfigureFailed(session: CameraCaptureSession) {}
                    }, null)

                    backCameraRatation = getCameraRotation(manager, cameraId)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                }
            }, handler)

            // ImageReaderのlistenerを設定
            imageReader.setOnImageAvailableListener({
                val image = it.acquireLatestImage()

                if (image != null && image.planes != null && image.planes.isNotEmpty()) {
                    synchronized(frameData) {
                        val buffer = image.planes[0].buffer
                        val bytes = ByteArray(buffer.remaining())
                        buffer.get(bytes)
                        frameData.backBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        image.close()
                    }
                }
            }, handler)
        }

        return localVideoStream
    }

    private fun getCameraId(manager: CameraManager, cameraDirection: Int): String? {
        val cameraIdList = manager.cameraIdList

        for (cameraId in cameraIdList) {
            val characteristics = manager.getCameraCharacteristics(cameraId)
            val lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING)

            if (lensFacing != null && lensFacing == cameraDirection) {
                return cameraId
            }
        }

        return null
    }

    private fun getCameraRotation(manager: CameraManager, cameraId: String): Int {
        val characteristics = manager.getCameraCharacteristics(cameraId)
        characteristics.let {
            val rotation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)
            rotation?.let {
                return it
            }
        }
        return 0
    }

    private fun combineImages(mainBitmap: Bitmap, subBitmap: Bitmap, mainRotation: Int = 0, subRotation: Int = 0): Bitmap {
        // メイン画像をキャンバスに描画
        val resultBitmap = Bitmap.createBitmap(mainBitmap.width, mainBitmap.height, mainBitmap.config)
        val canvas = Canvas(resultBitmap)
        val matrix = Matrix()
        matrix.postRotate(mainRotation.toFloat(), mainBitmap.width / 2f, mainBitmap.height / 2f)
        canvas.drawBitmap(mainBitmap, matrix, null)

        // サブ画像を小さくスケーリング
        val scale = 1f / 3
        val scaledSubBitmap = Bitmap.createScaledBitmap(
            subBitmap,
            (mainBitmap.width * scale).toInt(),
            (mainBitmap.height * scale).toInt(),
            false
        )

        // サブ画像の位置調整と回転
        val padding = 16f
        val left = mainBitmap.width - scaledSubBitmap.width - padding
        val top = mainBitmap.height - scaledSubBitmap.height - padding
        val matrix2 = Matrix().apply {
            postRotate(subRotation.toFloat(), scaledSubBitmap.width / 2f, scaledSubBitmap.height / 2f)
            postTranslate(left - scaledSubBitmap.width / 2f, top - scaledSubBitmap.height / 2f)
        }
        val rotatedSubBitmap = Bitmap.createBitmap(
            scaledSubBitmap, 0, 0,
            scaledSubBitmap.width, scaledSubBitmap.height, matrix2, true
        )

        // サブ画像をキャンバスに描画
        canvas.drawBitmap(rotatedSubBitmap, left, top, null)

        return resultBitmap
    }

//    private fun combineImages(mainBitmap: Bitmap, subBitmap: Bitmap): Bitmap {
//        // メイン画像をキャンバスに描画
//        val resultBitmap = Bitmap.createBitmap(mainBitmap.width, mainBitmap.height, mainBitmap.config)
//        val canvas = Canvas(resultBitmap)
//        canvas.drawBitmap(mainBitmap, Matrix(), null)
//
//        // サブ画像を小さくスケーリング
//        val scale = 1f / 3
//        val bitmap = Bitmap.createScaledBitmap(
//            subBitmap,
//            (mainBitmap.width * scale).toInt(),
//            (mainBitmap.height * scale).toInt(),
//            false
//        )
//
//        // メイン画像の上にサブ画像を描画
//        val padding = 16f
//        val left = mainBitmap.width - bitmap.width - padding
//        val top = mainBitmap.height - bitmap.height - padding
//        canvas.drawBitmap(bitmap, left, top, null)
//
//        return resultBitmap
//    }

}