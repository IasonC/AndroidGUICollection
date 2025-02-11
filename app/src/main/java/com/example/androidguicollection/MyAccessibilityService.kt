package com.example.androidguicollection

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.app.Activity
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.content.Intent
import android.content.IntentFilter
import android.content.BroadcastReceiver
import android.content.Context
import kotlinx.coroutines.*

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.util.UUID
import kotlin.random.Random
import android.os.Bundle

import android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK
import android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME


class MyAccessibilityService : AccessibilityService() {

    private var job: Job? = null
    private var keyboardVisible: Boolean = false // flag for keyboard
    private var nodeEditText: AccessibilityNodeInfo? = null
    private val delayMillis: Long = 4000 // Delay time after last event
    private val MAX_DEPTH: Int = 1000
    private val KEYBOARD_OVERTOP: Int = 15 // pixels above top keys where Keyboard still active (heuristic)

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private val SCREENSIM: Boolean = true // true if Screensim dataset; False if Interactable dataset

    override fun onServiceConnected() {
        super.onServiceConnected()

        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_CLICKED or
                    AccessibilityEvent.TYPE_VIEW_SCROLLED or
                    AccessibilityEvent.TYPE_VIEW_CONTEXT_CLICKED or
                    AccessibilityEvent.TYPE_VIEW_LONG_CLICKED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
            flags = AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        this.serviceInfo = info
        Log.d("AccessibilityService", "Service connected and configured")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Check if the service is enabled and the event type is desired
        // of interest: TYPE_WINDOW_STATE_CHANGED, TYPE_WINDOW_CONTENT_CHANGED are brittle to Spotify videos etc

        if (event?.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED ||
            event?.eventType == AccessibilityEvent.TYPE_VIEW_CONTEXT_CLICKED ||
            event?.eventType == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED ||
            event?.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED
        ) {
            // Cancel the previous coroutine if a new event comes in
            job?.cancel()

            // Launch a new coroutine
            job = CoroutineScope(Dispatchers.Main).launch {
                delay(delayMillis)
                processAggregatedEvent(event)
            }
        }
    }

    private fun processAggregatedEvent(event: AccessibilityEvent?) {
        // Process the aggregated event here
        event?.let {
            //Log.d("MyAccessibilityService", "Aggregated event processed: ${event.eventType}")
            //Log.d("onAccessibilityEvent", "Service is enabled")
            Log.d("processAggregatedEvent", "Aggregated signal --> Screen is changing...")

            Handler(Looper.getMainLooper()).postDelayed({
                val uuid: UUID = UUID.randomUUID()
                val uuidString: String = uuid.toString()

                // On-Click Listener for Screenshot
                sendBroadcastToMainActivity(uuidString)                                             // TODO Rewrite with MediaProjection

                // START AccessibilityNode process to get & save bbox to JSON
                getBbox(uuidString)
            }, 1000)                                                                                // TODO less delay, more dynamic
        }
    }

    private fun sendBroadcastToMainActivity(uuidString: String) {
        Log.d("sendBroadcastToMainActivity", "Sending Broadcast ACTION_ACCESSIBILITY_EVENT...")
        val intent = Intent("com.example.androidguicollection.ACTION_ACCESSIBILITY_EVENT")
        intent.putExtra("uuidString", uuidString) // UUID String name for screenshot
        sendBroadcast(intent)
    }

    private fun getBbox(uuidString: String) {
        Log.d("getBbox", "Get BBOX to JSON")

        //var foregroundClickable : MutableList<Pair<AccessibilityNodeInfo,IntArray>>
        val rootNode = rootInActiveWindow
        rootNode?.let {
            val clickableMutable: MutableList<Pair<AccessibilityNodeInfo, IntArray>> =
                mutableListOf()
            val scrollableMutable: MutableList<AccessibilityNodeInfo> = mutableListOf()
            traverseAccessibilityTree(clickableMutable, scrollableMutable, rootNode, 0)
            //hashMap.put("bbox", intArrayOf(1,2,3,4)
            //val clickable = clickableMutable.toTypedArray() //arrayOf(hashMap)

            val clickable: MutableList<HashMap<String, IntArray>> = mutableListOf()
            var keyboardBboxesProg: MutableList<Pair<AccessibilityNodeInfo, IntArray>> =
                mutableListOf()

            var minyKeyboard: Int = 5000 // large number (greater than most phones' resolution)
            val rootRect = Rect()
            rootNode.getBoundsInScreen(rootRect)
            val rootBottom = rootRect.bottom // get low y position for bboxes if keyboard active
            if (keyboardVisible == true) {
                Log.d("KEYBOARD", "Keyboard visible in main getBbox fun")
                keyboardBboxesProg = detectKeyboardBboxes()
                keyboardBboxesProg.forEach { (_, intArray) ->
                    minyKeyboard = minOf(minyKeyboard, intArray[1])
                    var hashMap: HashMap<String, IntArray> = HashMap<String, IntArray>()
                    hashMap.put("bbox", intArray) //intArrayOf(x1,y1,x2,y2))
                    clickable.add(hashMap)
                }
                minyKeyboard = minOf(
                    rootBottom,
                    minyKeyboard - KEYBOARD_OVERTOP
                ) // WORKS FOR SPOTIFY, NOT PHONE
            } else {
                minyKeyboard = rootBottom
            }

            val foregroundClickable = filterForegroundClickableNodes(clickableMutable)
            foregroundClickable.forEach { (_, intArray) ->
                /*val x1 = bounds.left  // Top-left x coordinate
                val y1 = bounds.top   // Top-left y coordinate
                val x2 = bounds.right // Bottom-right x coordinate
                val y2 = bounds.bottom// Bottom-right y coordinate*/
                //Log.d("getBbox", "intArray ${intArray[1]},${intArray[3]}")

                // Alter bbox intArray if keyboard active --> no bbox below keyboard
                if (intArray[3] > minyKeyboard) {
                    /*Log.d("BBOX", "Node below keyboard -- " +
                            "node CLASS ${node.className}, PACKAGE ${node.packageName}, " +
                            "DESC ${node.getContentDescription()}, TEXT ${node.getText()}")*/
                    intArray[3] = minyKeyboard
                }

                var hashMap: HashMap<String, IntArray> =
                    HashMap<String, IntArray>() // bbox: [tl_x, tl_y, br_x, br_y]
                hashMap.put("bbox", intArray) //intArrayOf(x1,y1,x2,y2))
                //Log.d("traverseAccessibilityTree", "hashMap: $x1,$y1,$x2,$y2")

                if (intArray[3] > intArray[1]) { // filter inverted bboxes
                    clickable.add(hashMap) // Add to mutable clickable Array of hashmaps
                }
            }

            if (SCREENSIM == true) {
                screensimData(uuidString, foregroundClickable)
            } else {
                Log.d("getBbox", "traverseAccessibilityTree done, saving JSON")
                saveJson(uuidString, clickable)
                updateListUUID(uuidString, "all_uuid_scraped.json", "uuids")

                Handler(Looper.getMainLooper()).postDelayed({
                    // 500ms to process bbox (save JSON) & screenshot (save PNG)
                    // then, simulate click on random interactable to continue
                    clickScrollRandInteractable(
                        foregroundClickable,
                        keyboardBboxesProg,
                        scrollableMutable,
                        keyboardVisible
                    )
                    keyboardVisible = false // reinitialise for next state
                    nodeEditText = null // reinitialise
                }, 500)
            }
        }
    }

    private fun screensimData(
        uuidString : String,
        foregroundClickable : MutableList<Pair<AccessibilityNodeInfo,IntArray>>
    ) {
        // create hashmap entry in total domain map

        val uuidTot: UUID = UUID.randomUUID()
        val uuidStringTot: String = uuidTot.toString()
        val totalName = "SAMESTATE_$uuidStringTot"

        // Add to list of same-state
        updateListUUID(uuidString, "domain_map.json", totalName)
        updateListUUID(uuidString, "all_uuid_scraped.json", "uuids")

        serviceScope.launch {
            val uuid2: UUID = UUID.randomUUID()
            val uuidString2: String = uuid2.toString()

            // On-Click Listener for Screenshot
            delay(1500L)
            sendBroadcastToMainActivity(uuidString2)

            // Add to list of same-state
            updateListUUID(uuidString2, "domain_map.json", totalName)
            updateListUUID(uuidString2, "all_uuid_scraped.json", "uuids")

            delay(5000L) // 4sec delay
        }



        /*serviceScope.launch {
            val tot: Int = Random.nextInt(3,5)
            for (i in 1..tot) {
                // wait 1 sec -> get screenshot and update uuid list
                Log.d("SCREENSIM", "Getting same-state $i of $tot........")
                val uuid: UUID = UUID.randomUUID()
                val uuidString: String = uuid.toString()

                // On-Click Listener for Screenshot
                delay(1500L)
                sendBroadcastToMainActivity(uuidString)

                // Add to list of same-state
                updateListUUID(uuidString, "domain_map.json", totalName)
                updateListUUID(uuidString, "all_uuid_scraped.json", "uuids")

                delay(4000L) // 4sec delay

                // Stop the foreground service
                val serviceIntent = Intent(applicationContext, ScreenCaptureService::class.java)
                stopService(serviceIntent)
            }
        }*/
        Log.i("SCREENSIM", "State for-loop over")

        val scrollableMutableEmpty: MutableList<AccessibilityNodeInfo> = mutableListOf()
        var keyboardBboxesProgEmpty: MutableList<Pair<AccessibilityNodeInfo, IntArray>> =
            mutableListOf()
        clickScrollRandInteractable(
            foregroundClickable,
            keyboardBboxesProgEmpty,
            scrollableMutableEmpty,
            keyboardVisible
        )
        keyboardVisible = false // reinitialise for next state
        nodeEditText = null // reinitialise

        Log.d("SCREENSIM", "FINISHED STATE $totalName")
        //Thread.sleep(500)
    }

    private fun detectKeyboardBboxes() : MutableList<Pair<AccessibilityNodeInfo, IntArray>> {
        val bboxes = mutableListOf<Pair<AccessibilityNodeInfo, IntArray>>()
        val windows = this.windows
        //Log.d("KEYBOARD", "Windows size ${windows.size}")
        for (window in windows) {
            //Log.d("KEYBOARD", "window type ${window.type}")
            if (window.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                val rootNode = window.root ?: continue
                val queue = mutableListOf<AccessibilityNodeInfo>()
                queue.add(rootNode)
                while (queue.isNotEmpty()) {
                    val currentNode = queue.removeAt(0)
                    //Log.d("KEYBOARD", "Node class ${currentNode.className}")
                    //val description = currentNode.contentDescription?.toString() ?: ""
                    //Log.d("KEYBOARD", "DESCRIPTION $description")
                    if (!currentNode.className.toString().contains("android.view.View") &&
                        !currentNode.className.toString().contains("android.widget.FrameLayout")) {
                        val bounds = Rect()
                        currentNode.getBoundsInScreen(bounds)
                        bboxes.add(Pair(
                            currentNode, intArrayOf(bounds.left, bounds.top, bounds.right, bounds.bottom)
                        ))
                        //Log.d("Button BBox", "Button ${currentNode.text} has bounds: $bounds")
                    }
                    for (i in 0 until currentNode.childCount) {
                        queue.add(currentNode.getChild(i))
                    }
                }
            }
        }
        return bboxes
    }

    private fun traverseAccessibilityTree(
        //clickableMutable: MutableList<HashMap<String, IntArray>>, nodeInfo: AccessibilityNodeInfo, depth: Int) {
        clickableMutable: MutableList<Pair<AccessibilityNodeInfo, IntArray>>,
        scrollableMutable: MutableList<AccessibilityNodeInfo>,
        nodeInfo: AccessibilityNodeInfo,
        depth: Int
    ) {

        if (depth >= MAX_DEPTH) {
            // Log a message indicating the node is skipped due to maximum depth reached
            Log.d("AccessibilityService", "Node skipped: Maximum depth reached.")
            return
        }

        if (keyboardVisible == false && nodeInfo.className != null) {
            val className = nodeInfo.className.toString()
            //Log.d("KEYBOARD", "Depth $depth, CLASS name $className")
            if (className.contains("EditText")) {
                keyboardVisible = true
                //Log.d("KEYBOARD", "Keyboard Visible")
            }
        }

        // Check if the node is clickable --> Get & save bbox of this node
        if (nodeInfo.isClickable && nodeInfo.isVisibleToUser) {
            val bounds = Rect()
            // Get the bounds in screen coordinates
            nodeInfo.getBoundsInScreen(bounds)
            clickableMutable.add(Pair(nodeInfo, intArrayOf(bounds.left,bounds.top,bounds.right,bounds.bottom)))
        }

        if (nodeInfo.isScrollable) {
            scrollableMutable.add(nodeInfo)
        }
        if (nodeInfo.className.contains("EditText")) {
            Log.d("traverseAccessibilityTree", "Found EditText Node")
            nodeEditText = nodeInfo
        }

        // Loop through the child nodes and recursively traverse the tree
        for (i in 0 until nodeInfo.childCount) {
            val childNode = nodeInfo.getChild(i)
            childNode?.let {
                // Recursive call to traverse the child node with increased depth
                traverseAccessibilityTree(clickableMutable, scrollableMutable, childNode, depth + 1)
            }
        }
    }

    private fun parentRelation(node1 : AccessibilityNodeInfo, node2 : AccessibilityNodeInfo) : Boolean {
        if (node1.parent!=null && node2.parent!=null && node1.parent==node2.parent) {return true}
        else if (node1.parent!=null && node1.parent==node2) {return true}
        else if (node2.parent!=null && node1==node2.parent) {return true}
        else {return parentRecursion(node1,node2) || parentRecursion(node2,node1)}
    }

    private fun parentRecursion(node1 : AccessibilityNodeInfo, node2 : AccessibilityNodeInfo) : Boolean {
        if (node1 == rootInActiveWindow || node1.parent == null) {return false}
        else if (node1.parent == node2) {return true}
        else {return parentRecursion(node1.parent, node2)}
    }

    private fun overlap(bounds1 : IntArray, bounds2 : IntArray) : Boolean {
        return (bounds1[1]<bounds2[3] && bounds1[3]>bounds2[1] &&   // y check
                bounds1[0]<bounds2[2] && bounds1[2]>bounds2[0])     // x check
                //bounds1[0]<bounds2[0] && bounds1[2]>bounds2[2])
    }

    private fun areaSmall(bounds1 : IntArray, bounds2 : IntArray) : Boolean {
        val area1 : Int = (bounds1[2]-bounds1[0]) * (bounds1[3]-bounds1[1])
        val area2 : Int = (bounds2[2]-bounds2[0]) * (bounds2[3]-bounds2[1])
        return area2 < 20*area1 // TRUE if area1 is more than 10% of area2
    }

    private fun filterForegroundClickableNodes(clickableNodes: MutableList<Pair<AccessibilityNodeInfo,IntArray>>) :
            MutableList<Pair<AccessibilityNodeInfo,IntArray>> {
        val foregroundNodes : MutableList<Pair<AccessibilityNodeInfo,IntArray>> = mutableListOf()

        Log.d("filterClickable", "clickable size ${clickableNodes.size}, indices ${clickableNodes.indices}")
        clickableNodes.reverse()
        for (i in clickableNodes.indices) {
            val (node1, bounds1) = clickableNodes[i]

            for (j in i + 1 until clickableNodes.size) {
                val (node2, bounds2) = clickableNodes[j]

                // Check if bounds1 is obscured by bounds2
                if (overlap(bounds1,bounds2) && areaSmall(bounds1,bounds2) && !parentRelation(node1,node2)) {
                    //Rect.intersects(bounds1, bounds2)
                    val updatedPair = Pair(node2, intArrayOf(bounds2[0],bounds2[1],bounds2[2],bounds1[1]))
                    clickableNodes[j] = updatedPair
                }
            }

            foregroundNodes.add(Pair(node1, bounds1))
        }

        return foregroundNodes
    }

    private fun saveJson(uuidString: String, clickable: MutableList<HashMap<String, IntArray>>) {
        data class BBOXES(val id: String, val clickable: MutableList<HashMap<String, IntArray>>)
        val bboxes = BBOXES(uuidString, clickable)

        val gson = Gson()
        val jsonData = gson.toJson(bboxes)

        // Save JSON to external storage
        saveJsonToExternalStorage("$uuidString.json", jsonData, "BBOX")
    }

    private fun updateListUUID(uuidString: String, fileName: String, key: String) {
        val uuidFile = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName)
        if (!uuidFile.exists()) {
            uuidFile.writeText("{}")  // Write an empty JSON object to the file
        }

        val jsonString = uuidFile.readText(Charsets.UTF_8)
        val mapType = object : TypeToken<MutableMap<String, MutableList<String>>>() {}.type

        // read file
        val jsonObject : MutableMap<String, MutableList<String>> = Gson().fromJson(jsonString, mapType)

        // update new list item
        val list = jsonObject.getOrPut(key) { mutableListOf() }
        list.add(uuidString)

        // save to file
        val gson = Gson()
        val jsonStringUpdate = gson.toJson(jsonObject)
        saveJsonToExternalStorage(fileName, jsonStringUpdate, "UUID")
    }

    private fun saveJsonToExternalStorage(fileName: String, jsonData: String, logkey: String) {
        if (isExternalStorageWritable()) {
            val file = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName)
            var fileWriter: FileWriter? = null
            try {
                fileWriter = FileWriter(file)
                fileWriter.write(jsonData)
                Log.d("saveJsonToExternalStorage", "JSON $logkey File saved successfully at ${file.absolutePath}")
            } catch (e: IOException) {
                Log.e("saveJsonToExternalStorage", "Error JSON $logkey writing to file", e)
            } finally {
                fileWriter?.close()
            }
        } else {
            Log.e("MainActivity", "External storage is not writable -- key $logkey")
        }
    }

    private fun clickScrollRandInteractable(
        foregroundClickable : MutableList<Pair<AccessibilityNodeInfo,IntArray>>,
        keyboardBboxesProg : MutableList<Pair<AccessibilityNodeInfo,IntArray>>,
        scrollableMutable : MutableList<AccessibilityNodeInfo>,
        keyboardVisible : Boolean
    ) {
        // 30% chance to scroll, 70% to click rand interactable (percentages heuristic for exploration)
        if (Random.nextInt(0,10) < 3 && scrollableMutable.size > 0 && !keyboardVisible && !SCREENSIM) {
            Log.d("NEXT", "Auto-SCROLL rand interactable...")
            val randIdx: Int = Random.nextInt(0, scrollableMutable.size)
            Log.d("NEXT", "Scroll Size ${scrollableMutable.size}, Randint $randIdx")
            val randScrollNode: AccessibilityNodeInfo = scrollableMutable[randIdx]
            randScrollNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) // Scroll
        }
        else if (Random.nextInt(0,10) < 6 && nodeEditText != null && !SCREENSIM) {
            Log.d("NEXT", "Input TEXT to Search")
            val randomText = generateRandomString(Random.nextInt(1,6), keyboardBboxesProg.size)
            Log.d("NEXT", "Random Text = $randomText")
            inputText(randomText)
        }
        else if (foregroundClickable.size > 0) {
            Log.d("NEXT", "Auto-CLICK rand interactable...")
            val randIdx: Int =
                Random.nextInt(0, foregroundClickable.size) // Get rand int 0...size(list)
            val randNode: AccessibilityNodeInfo =
                foregroundClickable[randIdx].first // get node at that index
            randNode.performAction(AccessibilityNodeInfo.ACTION_CLICK) // click on interactable node
        }
        else {
            Log.e("clickRandInteractable", "No interactables detected... Stopping...")
            closeApp()
        }
    }

    private fun generateRandomString(length: Int, keyboardKeyNum: Int): String {
        var chars: String
        if (keyboardKeyNum < 40) { // probably a numerical keyboard for telephone app
            chars = "01234567889"
        }
        else { // probably alphanumerical standard keyboard
            chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        }
        return (1..length)
            .map { chars.random() }
            .joinToString("")
    }

    private fun inputText(text: String) {
        nodeEditText?.apply{
            Log.d("inputText", "Inputting text = $text")
            performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            val arguments = Bundle()
            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        }
    }

    /* Checks if external storage is available for read and write */
    private fun isExternalStorageWritable(): Boolean {
        return Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
    }

    private fun startScreenshot() {
        // UNUSED -- Cannot start Activity from Service in Android 10+ (security issue)
        Log.d("startScreenshot", "Calling ScreenshotActivity with Intent...")
        val intent = Intent(this, ScreenshotActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    override fun onInterrupt() {
        Log.e("Accessibility Service", "Service Interrupted")
    }

    private fun closeApp() {
        performGlobalAction(GLOBAL_ACTION_BACK) // Simulate back press
        performGlobalAction(GLOBAL_ACTION_HOME) // Go to home screen
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel() // Cancel the coroutine scope when service is destroyed
    }
}