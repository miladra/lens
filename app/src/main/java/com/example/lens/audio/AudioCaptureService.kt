package com.example.lens.audio

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.*
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import kotlin.concurrent.thread

class AudioCaptureService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var audioRecordMic: AudioRecord? = null
    private var audioRecordSystem: AudioRecord? = null
    private var isRecording = false

    companion object {
        const val ACTION_START = "START"
        const val ACTION_STOP = "STOP"
        const val EXTRA_RESULT_DATA = "RESULT_DATA"
        const val EXTRA_FILE_PATH = "FILE_PATH"
        const val ACTION_FINISHED = "com.example.lens.AUDIO_FINISHED"
        
        private const val NOTIFICATION_ID = 101
        private const val CHANNEL_ID = "AudioCaptureChannel"
        private const val SAMPLE_RATE = 16000 
        private const val DEFAULT_BUFFER_SIZE = 16384
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START) {
            val filePath = intent.getStringExtra(EXTRA_FILE_PATH)
            val resultData: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(EXTRA_RESULT_DATA)
            }

            // 1. MUST establish foreground IMMEDIATELY with MIC and PROJECTION types
            establishForeground()

            if (resultData != null && filePath != null) {
                // 2. Delay recording start to allow system to register foreground state
                thread {
                    Thread.sleep(1500) // Slightly longer delay to be safe
                    startRecording(resultData, File(filePath))
                }
            } else {
                stopSelf()
            }
        } else if (intent?.action == ACTION_STOP) {
            stopRecording()
        }
        return START_NOT_STICKY
    }

    private fun establishForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Audio Capture", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Lens Recording")
            .setContentText("Capturing audio for translation...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        // Explicitly set the types. Microphone type is mandatory for background mic access on Android 14+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
            try {
                startForeground(NOTIFICATION_ID, notification, type)
            } catch (e: Exception) {
                Log.e("AudioCaptureService", "startForeground with types failed: ${e.message}")
                startForeground(NOTIFICATION_ID, notification)
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun showToast(message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun startRecording(resultData: Intent, file: File) {
        // Final permission verification
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            showToast("Microphone permission was revoked.")
            stopSelf()
            return
        }

        val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = try {
            mpManager.getMediaProjection(Activity.RESULT_OK, resultData)
        } catch (e: Exception) {
            Log.e("AudioCaptureService", "Failed to get MediaProjection: ${e.message}")
            null
        }
        
        if (projection == null) {
            showToast("Screen capture permission is required.")
            stopSelf()
            return
        }
        mediaProjection = projection

        // Register callback to handle projection stop
        projection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                Log.d("AudioCaptureService", "MediaProjection stopped")
                stopRecording()
            }
        }, Handler(Looper.getMainLooper()))

        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .build()

        // System audio is natively Stereo on most devices; capturing in Mono can fail on some hardware with headphones
        val sysFormat = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                .build()
        } else format

        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            .let { if (it > 0) Math.max(it, DEFAULT_BUFFER_SIZE) else DEFAULT_BUFFER_SIZE }
        
        val sysBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT)
            .let { if (it > 0) Math.max(it, DEFAULT_BUFFER_SIZE * 2) else DEFAULT_BUFFER_SIZE * 2 }

        // Initialize Microphone - Use standard constructor for better compatibility
        try {
            audioRecordMic = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
            
            if (audioRecordMic?.state == AudioRecord.STATE_INITIALIZED) {
                audioRecordMic?.startRecording()
                Log.d("AudioCaptureService", "Mic recording started successfully")
            } else {
                Log.e("AudioCaptureService", "Mic AudioRecord failed to initialize. State: ${audioRecordMic?.state}")
            }
        } catch (e: SecurityException) {
            showToast("Permission Error: Mic access blocked by system.")
            Log.e("AudioCaptureService", "SecurityException on mic start: ${e.message}")
        } catch (e: Exception) {
            Log.e("AudioCaptureService", "Mic error: ${e.message}")
        }

        // Initialize System Audio
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val config = AudioPlaybackCaptureConfiguration.Builder(projection)
                    .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                    .addMatchingUsage(AudioAttributes.USAGE_GAME)
                    .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                    .addMatchingUsage(AudioAttributes.USAGE_ASSISTANT)
                    .addMatchingUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .addMatchingUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .addMatchingUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .addMatchingUsage(AudioAttributes.USAGE_ALARM)
                    .build()
                
                audioRecordSystem = AudioRecord.Builder()
                    .setAudioPlaybackCaptureConfig(config)
                    .setAudioFormat(sysFormat)
                    .setBufferSizeInBytes(sysBufferSize)
                    .build()
                
                if (audioRecordSystem?.state == AudioRecord.STATE_INITIALIZED) {
                    audioRecordSystem?.startRecording()
                    Log.d("AudioCaptureService", "System audio recording started")
                }
            } catch (e: Exception) {
                Log.e("AudioCaptureService", "System audio error: ${e.message}")
            }
        }

        isRecording = true
        
        thread {
            val micBuffer = ShortArray(bufferSize / 2)
            val sysReadBuffer = ShortArray(sysBufferSize / 2)
            
            // Ring buffer for system audio to handle jitter and routing delays
            val sysRingBuffer = ShortArray(sysBufferSize * 2)
            var sysHead = 0
            var sysTail = 0
            var sysAvailable = 0
            
            try {
                FileOutputStream(file).use { outputStream ->
                    outputStream.write(ByteArray(44)) // WAV Header placeholder
                    
                    while (isRecording) {
                        // 1. Recover recording states if interrupted (e.g. by headphone plug-in)
                        if (audioRecordMic?.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                            try { audioRecordMic?.startRecording() } catch (e: Exception) {}
                        }
                        if (audioRecordSystem?.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                            try { audioRecordSystem?.startRecording() } catch (e: Exception) {}
                        }

                        // 2. Capture available system audio into ring buffer
                        val sysRead = try { 
                            audioRecordSystem?.read(sysReadBuffer, 0, sysReadBuffer.size, AudioRecord.READ_NON_BLOCKING) ?: 0 
                        } catch(e: Exception) { 0 }
                        
                        if (sysRead > 0) {
                            // Downmix Stereo to Mono for the ring buffer
                            for (i in 0 until sysRead step 2) {
                                val left = sysReadBuffer[i].toInt()
                                val right = if (i + 1 < sysRead) sysReadBuffer[i+1].toInt() else left
                                val mono = ((left + right) / 2).toShort()
                                sysRingBuffer[sysTail] = mono
                                sysTail = (sysTail + 1) % sysRingBuffer.size
                                if (sysAvailable < sysRingBuffer.size) sysAvailable++ else sysHead = (sysHead + 1) % sysRingBuffer.size
                            }
                        }

                        // 3. Read Microphone (blocking read acts as master timing)
                        val micRead = try { audioRecordMic?.read(micBuffer, 0, micBuffer.size) ?: 0 } catch(e: Exception) { 0 }
                        
                        if (micRead < 0) {
                            Thread.sleep(10)
                            continue
                        }

                        val samplesToProcess = if (micRead > 0) micRead else 0
                        if (samplesToProcess <= 0) {
                            if (!isRecording) break
                            Thread.sleep(5)
                            continue
                        }

                        val mixedBuffer = ByteArray(samplesToProcess * 2)
                        for (i in 0 until samplesToProcess) {
                            val m = micBuffer[i].toInt()
                            
                            // Get corresponding system sample from ring buffer, or 0 if silence
                            val s = if (sysAvailable > 0) {
                                val sample = sysRingBuffer[sysHead].toInt()
                                sysHead = (sysHead + 1) % sysRingBuffer.size
                                sysAvailable--
                                sample
                            } else 0
                            
                            val mixed = (m + s).coerceIn(-32768, 32767).toShort()
                            
                            mixedBuffer[i * 2] = (mixed.toInt() and 0xFF).toByte()
                            mixedBuffer[i * 2 + 1] = ((mixed.toInt() shr 8) and 0xFF).toByte()
                        }
                        outputStream.write(mixedBuffer)
                    }
                }
            }
catch (e: Exception) {
                Log.e("AudioCaptureService", "Error in recording loop", e)
            } finally {
                finalizeWavFile(file)
                Log.d("AudioCaptureService", "Recording closed. File size: ${file.length()} bytes")
                sendBroadcast(Intent(ACTION_FINISHED))
                stopForeground(true)
                stopSelf()
            }
        }
    }

    private fun finalizeWavFile(file: File) {
        if (!file.exists() || file.length() < 44) return
        val totalAudioLen = file.length() - 44
        val totalDataLen = totalAudioLen + 36
        val channels = 1
        val byteRate = 16 * SAMPLE_RATE * channels / 8

        val header = ByteArray(44)
        header[0] = 'R'.toByte(); header[1] = 'I'.toByte(); header[2] = 'F'.toByte(); header[3] = 'F'.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        header[8] = 'W'.toByte(); header[9] = 'A'.toByte(); header[10] = 'V'.toByte(); header[11] = 'E'.toByte()
        header[12] = 'f'.toByte(); header[13] = 'm'.toByte(); header[14] = 't'.toByte(); header[15] = ' '.toByte()
        header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0
        header[20] = 1; header[21] = 0
        header[22] = channels.toByte(); header[23] = 0
        header[24] = (SAMPLE_RATE and 0xff).toByte()
        header[25] = ((SAMPLE_RATE shr 8) and 0xff).toByte()
        header[26] = ((SAMPLE_RATE shr 16) and 0xff).toByte()
        header[27] = ((SAMPLE_RATE shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = (channels * 16 / 8).toByte()
        header[33] = 0; header[34] = 16; header[35] = 0
        header[36] = 'd'.toByte(); header[37] = 'a'.toByte(); header[38] = 't'.toByte(); header[39] = 'a'.toByte()
        header[40] = (totalAudioLen and 0xff).toByte()
        header[41] = ((totalAudioLen shr 8) and 0xff).toByte()
        header[42] = ((totalAudioLen shr 16) and 0xff).toByte()
        header[43] = ((totalAudioLen shr 24) and 0xff).toByte()

        try {
            RandomAccessFile(file, "rw").use { raf ->
                raf.seek(0)
                raf.write(header)
            }
        } catch (e: Exception) {
            Log.e("AudioCaptureService", "Header write failed")
        }
    }

    private fun stopRecording() {
        isRecording = false
        audioRecordMic?.apply { try { if (state == AudioRecord.STATE_INITIALIZED) stop() } catch(e: Exception) {}; release() }
        audioRecordSystem?.apply { try { if (state == AudioRecord.STATE_INITIALIZED) stop() } catch(e: Exception) {}; release() }
        audioRecordMic = null
        audioRecordSystem = null
        mediaProjection?.stop()
        mediaProjection = null
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
