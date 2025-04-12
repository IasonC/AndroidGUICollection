# Automated Data Collection Tools for Android GUIs
_Chaimalas* and Vyšniauskas* and Brostow, EXPLORER: Robust Collection of Interactable GUI Elements, 2025_ [*Equal]

In the world of GUI Automation, collecting and labelling quality and realistic GUI usability data at scale is a challenging task, particularly given that many benchmarking datasets (e.g. RICO, VINS, etc.) are gathered by expensive crowd-sourcing. In this repository, we provide Android tooling written in Kotlin, in order to automatically traverse any GUI application in modern Android devices and automatically collect GUI screenshots and corresponding ground-truth labels for the following downstream GUI Automation tasks:
- **Interactable Detection**: GUI screenshots with labelled bounding boxes for tappable elements, used to train tappability/clickability detector models (e.g. FCOS, YOLO, RetinaNet, Faster R-CNN)
- **Screen Similarity**: GUI screenshots labelled into distinct groups, used to train similarity discriminator models

We train Machine Vision models and implement these downstream tasks in our main "Explorer" repository, available at [https://github.com/varnelis/Explorer](https://github.com/varnelis/Explorer).

## Data Collection Code

```activity_main.xml```
- Defines UI of the initial app ```AndroidGUICollection``` that queries user for storage/MediaProjection permissions

```MainActivity.kt```
- Launches initial app ```AndroidGUICollection``` and sets required storage/MediaProjection permissions
- Listens for ```ACTION_ACCESSIBILITY_EVENT``` events asserted by the background ```MyAccessibilityService``` and launches ```ScreenCaptureService``` as foreground service to capture current screenshot

```MyAccessibilityService.kt```
- Runs in background as accessibility service
- Collects interactable elements or same-state screens (depending on collection mode), performs random UI action to simulate real user clicking through target app

```ScreenCaptureService.kt```
- Started by ```MainActivity``` as foreground service
- Captures current screenshot using MediaProjection API; this is compliant with API 29+ standard

```AndroidManifest.xml```
- Defines core structure and permissions of the app including the foreground and accessibility services

```accessibility_service_config.xml```
- Registers the accessibility service to filter for all ```ACTION_ACCESSIBILITY_EVENT``` events

## Usage
1. Deploy ```AndroidGUICollection``` app to an Android device. You can do this by opening this project in Android Studio (inside top directory _AndroidGUICollection_) and clicking ```Run``` to install and run the project as an app in a connected device (USB Debugging enabled in device and ADB configured).
2. Open app ```AndroidGUICollection``` and click ```Storage Permissions``` and ```MediaProj API Permissions``` buttons. This gives the app permission to take screenshots of the top-level screen using MediaProjection APIs and to save them to internal device storage.
3. Enable the accessibility service ```AccessibilityScraper``` which was installed on the device alongside the ```AndroidGUICollection``` app. In Android API 29, this is found in _Settings > Accessibility > Installed services_.
4. Open a target app.
5. The ```AccessibilityScraper``` will run in the background and actuate the target app opened in the foreground.

The behaviour in Step 5 for the target app depends on whether the ```AccessibilityScraper``` service in the app has been installed with the Interactable Detection or Screen Similarity configuration. To set this configuration in the source code, set the ```SCREENSIM``` private value in ```app/src/main/java/com/example/androidguicollection/MyAccessibilityService.kt``` to Boolean ```false``` or ```true``` respectively, and re-install the app in the Android device.

### Interactable Detection Data Collection
If ```SCREENSIM == false```, the actuation of the target app is:
1. Screenshot current screen, save to Internal Storage within 500ms as ```<uuid>.png```, and keep track of UUID by updating ```all_uuid_scraped.json```.
2. Traverse accessibility-tree hierarchy of active foreground via BFS in ```MyAccessibilityService.traverseAccessibilityTree``` to extract all tappable/clickable and scrollable elements, then get ground-truth tappable bboxes in ```MyAccessibilityService.getBbox``` and save to JSON ```<uuid>.json``` as ```{<uuid>: List[bboxes]}```.
3. Do a random UI action:
    - If device keyboard is visible/selected, 60% chance to inject 1-6 character random string & 40% chance to tap random tappable element;
    - Else, 30% chance to scroll down inside random scrollable element and 70% chance to tap random tappable element.
4. Wait 4000ms for loading events to settle; this is heuristic and depends on device speed, internet connection (if applicable to target app) etc. Change by setting ```delayMillis``` in ```MyAccessibilityService.kt```.
5. Repeat from Step 1. _Note: The UI action performed in Step 3 will trigger a ```ACTION_ACCESSIBILITY_EVENT```, which is detected by the ```MainActivity``` --> takes screenshot and calls again from Step 1._

### Screen Similarity Data Collection
If ```SCREENSIM == true```, the actuation of the target app is:
1. Screenshot current screen twice within 5000ms and save to Internal Storage ```<uuid1>.png``` and ```<uuid2>.png```, and keep track of both UUIDs by updating ```all_uuid_scraped.json```.
2. Label the two screenshots into the same group (i.e. labelled same-state) by updating ```domain_map.json``` with new group entry ```{<group-uuid>: [uuid1, uuid2]}```.
3. Steps 3-5 same as case of ```SCREENSIM == false``` above.

## Citation
If you utlize our Android-based data collection and labelling in your GUI Automation research, please consider citing our work:
```<stump ArXiv URL>```

Also refer to our main ["Explorer" repository](https://github.com/varnelis/Explorer) for the paper implementation.