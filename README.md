# Virtual-Screen-Gesture-Recognition-App
This virtual screen system can use gestures to interact with mobile phones in the air.
All functions are developed and tested successfully on Pixel4 and Android Studio 4.0.

## How to use this app:
1. Once you have successfully launched the app, make sure it gets permission to display over other apps. Then long-press the app button to enter app info, and allow the app to use the camera.
2. Type 'adb shell setprop log.tag.MainActivity VERBOSE' in the terminal and run again.
3. Launch the app. After entering the app, press the "start virtual screen" button to start the service.
4. A window that your finger can drag will appear. Inside it is the user in the front camera, convenient for users to observe themselves. There will also be a small red dot, which represents the location of the index fingertip of your pointing hand on the screen. Think of it as the mouse displayed on the computer screen. 
5. Make a holding gesture* to the camera with one hand, then use another hand to make a pointing gesture. You would find the red dot will move with your pointing fingertip.
.<div align=center><img src="https://github.com/caiyc2000/Virtual-Screen-Gesture-Recognition-App/blob/master/demo_start.gif" width="202" height="413" /></div>

6. Do whatever you want to do. Below is the demo of 'clicking' the calendar icon remotely.
.<div align=center><img src="https://github.com/caiyc2000/Virtual-Screen-Gesture-Recognition-App/blob/master/demo_click.gif" width="202" height="413" /></div>


*Holding gesture: Use the thumb and index finger to form a rectangle, pretending to hold a smartphone in the air.
*Pointing gesture: Straighten the index finger and bend the other fingers, like a "1" gesture.


## There are three main steps:
1. First, we will recognize the holding gesture, which means entering the remote control mode.
2. After entering the remote control mode, recognize the gesture of the other hand for a "click "and track its movement.
3. If a "click" is recognized, a touch screen event will be sent to the system. 


## How to define a "click":  
The premise is that the hand is a pointing gesture. 
Within a certain period, the tip of the index finger is closer to the mobile phone than the wrist and then becomes farther away.
The relative depth info comes from the z coordinate of hand results in Mediapipe Hands.



## Main contributions of this project:
### 1. Recognize gestures: Use the geometric relationship of landmarks to map gestures

I used Mediapipe Hands to get normalized 3D hand landmarks. The depth information is relative to the wrist.
You can learn more about Mediapipe Hands from https://google.github.io/mediapipe/solutions/hands.html. Its example code was in https://github.com/jiuqiant/mediapipe_multi_hands_tracking_aar_example.
Using landmarks, I can tell whether it's straight or bent for each finger. I then mapped the state of the fingers to a custom set of gestures, including the holding and pointing gestures.


### 2. Rewrite the camera API (from CameraX to Camera2):

To realize my function, I have rewritten the CameraX API used by Mediapipe. I keep the application of CameraX API in the top-level code but use the method of Camera2 to realize the function of CameraX. Therefore, it seems that Mediapipe still uses CameraX, but it actually calls Camera2.
Also ,I rewritten surfaceTexture to CustomSurfaceTexture
I referenced the code here https://github.com/google/mediapipe/issues/513.


### 3. To keep Mediapipe always run in the background: Rewrite the activity as a service

First, I need to enter my app to enable the virtual screen function. But I have to log out of my interface to use my functions in other interfaces, so it still runs in the background even if I press the home button to launch my application. Therefore, I need to extend its life cycle. So I rewrote the activity as a service.


### 4. Provide visual feedback: Request "ALERT_WINDOW" permission

Usually, When we use mobile phones, we touch the screen to know exactly where we want to click. But the virtual screen is different because the hand is hanging in the air and has no direct contact with the screen, so we need cursor-like visual feedback to tell us where the finger tip is.
I used a small red dot as visual feedback.
And this tiny red dot must be on the surface of all applications, so I have to request "ALERT_WINDOW" permission.


### 5. Inject events into another app: Obtain system permission

To simulate the touch screen event in other applications, you must obtain system permissions. I implemented this function in the code using the command line.



## Personalized adjustments that can be made:  
How to define a click, including its duration, and how to judge whether it is closer or farther from the phone.


