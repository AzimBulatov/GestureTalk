package com.example.gesturetalk.utils

object Constants {
    const val TAG = "GestureTalk"
    const val CAMERA_PERMISSION_REQUEST_CODE = 100
    const val EMPTY_GESTURE = "---"
    
    // Model configuration
    const val MODEL_INPUT_SIZE = 224
    const val MODEL_CHANNELS = 3
    
    // Camera configuration
    const val CAMERA_FPS = 30
    
    // Camera resolution presets
    const val RESOLUTION_SD_WIDTH = 480
    const val RESOLUTION_SD_HEIGHT = 640
    const val RESOLUTION_HD_WIDTH = 720
    const val RESOLUTION_HD_HEIGHT = 1280
    const val RESOLUTION_FHD_WIDTH = 1080
    const val RESOLUTION_FHD_HEIGHT = 1920
    
    // Default resolution (will be overridden by settings)
    const val ANALYSIS_RESOLUTION_WIDTH = RESOLUTION_HD_WIDTH
    const val ANALYSIS_RESOLUTION_HEIGHT = RESOLUTION_HD_HEIGHT
}
