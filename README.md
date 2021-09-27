# Virtual-Screen-Gesture-Recognition-App
This is a virtual screen system that can use gestures to interact with mobile phones in the air.
All funtions are develped and tested successfully on Pixel4.

How to use this app:
After entering the app, press the "start virtual screen" button to start the service.
A window that can be dragged by hand will appear, and inside it is the user in the front camera, which is convenient for users to observe themselves.
There will also be a small red dot, which represents the location of the index finger tip of your pointing hand on the screen. Think of it as the mouse displayed on the computer screen. 

There are 3 main steps:
1. First, we will recognize the holding gesture, which means entering the remote control mode.
2. After entering the remote control mode, recognize the gesture of the other hand for a "click“ and track its move.
3. If a "click" is recognized, a touch screen event will be sent to the system. 

*Holding gesture: Use the thumb and index finger to form a rectangle, pretending to hold a smartphone in the air.
Pointing gesture: Straighten the index finger and bend the other fingers, like a "1" gesture.

How to define a "click":
The premise is that the hand is a pointing gesture. 
Within a certain period of time, the tip of the index finger is closer to the mobile phone than the wrist, and then becomes farther away.
The relative depth info comes from the z coordiate of hand results in Mediapipe Hands.

Main contributions of this project:

1. Recognize gestures: Use the geometric relationship of landmarks to map gestures

I used Mediapipe Hands to get normalized 3D hand landmarks. The depth information is relative to the wrist.
You can learn more about Mediapipe Hands from https://google.github.io/mediapipe/solutions/hands.html. Its example code was in https://github.com/jiuqiant/mediapipe_multi_hands_tracking_aar_example.
Using landmarks, I can tell whether it' s straight or bent for each finger. I then mapped the state of the fingers to a custom set of gestures, including the holding and pointing gesture.


2. Rewrite the camera API (from CameraX to Camera2):

In order to realize my own function, I have rewrittem the CameraX API used by Mediapipe. I keep the application of CameraX API in the top-level code, but use the method of Camera2 to realize the function of CameraX. Therefore, it seems that Mediapipe still uses CameraX, but it actually calls Camera2.
Also ,I rewritten surfaceTexture to CustomSurfaceTexture
I referenced the code here https://github.com/google/mediapipe/issues/513.


3. To keep Mediapipe always run in the background: Rewrite the activity as a service

First of all, I need to enter my app to enable the virtual screen function. But I have to log out of my interface to use my own functions in other interfaces, so even if I press the home button to launch my application, it still runs in the background. Therefore, I need to extend its life cycle. So i rewrote the activity as a service.


4. Provide visual feedback: Request “ALERT_WINDOW” permission

Normally, When we use mobile phones, we just touch the screen, so we know exactly where we want to click. But the virtual screen is different, because the hand is hanging in the air and has no direct contact with the screen, so we need a cursor-like visual feedback to tell us where the finger tip is.
I used a small red dot as visual feedback.
And this little red dot must be on the surface of all applications, so I have to request “ALERT_WINDOW” permission.


5. Inject events into another app: Obtain system permission

In order to simulate the touch screen event in other applications, you need to obtain system permissions. I implemented this function in the code using the command line.


Personalized adjustments that can be made:

How to define a click, including the duration of a click and how to judge whether it is closer or farther from the phone.


