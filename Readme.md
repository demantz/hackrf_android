HackRF Library for Android
==========================

This repository is a ported version of Michael Ossmann's libhackrf
([https://github.com/mossmann/hackrf/tree/master/host/libhackrf]
(https://github.com/mossmann/hackrf/tree/master/host/libhackrf))
library to work with Android 3.1+.



![hackrf_android](https://pbs.twimg.com/media/BzHt03EIIAEXTvN.jpg:large)

(photo by Dennis Mantz)

See [http://tech.mantz-it.com](http://tech.mantz-it.com) and @dennismantz for updates.


Implemented Features
--------------------
* Open HackRF (including the USB permission request)
* Reading Board ID from HackRF
* Reading Version from HackRF
* Reading Part ID and Serial Number from HackRF
* Setting Sample Rate of HackRF
* Setting Frequency of HackRF
* Setting Baseband Filter Width of HackRF
* Compute Baseband Filter Width for given Sample Rate
* Setting VGA Gain (Rx/Tx) of HackRF
* Setting LNA Gain of HackRF
* Setting Amplifier of HackRF
* Setting Antenna Port Power of HackRF
* Setting Transceiver Mode of HackRF
* Receiving from the HackRF using a BlockingQueue
* Transmitting to the HackRF using a BlockingQueue
* Get Transmission statistics
* Example App that shows how to use the library


Tested Devices
--------------

|    Device          | Does it work? | Comments                                  |     Tester       |
|:------------------:|:-------------:|:-----------------------------------------:|:----------------:|
| Oneplus One        |      yes      |                                           | KR0SIV           |
| Nexus 7 2012       |      yes      | ~ 2 Msps, Filewriter is too slow.         | demantz          |
| Nexus 7 2013       |      yes      | 15 Msps                                   | @kx3companion    |
| Nexus 4            | needs ROM...  | ...and Y cable. (http://goo.gl/rRyQVM)    | -                |
| Nexus 5            |      yes      | 15 Msps                                   | demantz          |
| Moto G             |      yes      | ~ 2 Msps                                  | @kx3companion    |
| Acer A500          |      yes      | ~ 5 Msps                                  | @digiital        |
| Samsung S3 LTE     |      yes      | 10 Msps, running CM 10.1.3                | dc1rdb           |
| Samsung S4         |      yes      |                                           | @digiital        |
| Samsung S4 LTE     |      yes      | 10 Msps                                   | Jonyweb          |
| Samsung S4 Zoom    |      yes      |                                           | Ace Rimmer       |
| Samsung S5         |      yes      |                                           | @simonroses      |
| Samsung Note 3     |      yes      |                                           | @M3atShi3ld      |
| Galaxy Tab S 8.4   |      yes      |                                           | Christophe Morel |
| Galaxy Tab S 10.5  |      yes      |                                           | Harald Pedersen  |
| HTC M8             |      yes      |                                           | dmaynor          |
| Sony Xperia Pro    |      no       | insufficient USB bus power                | anttivs          |
| Sony Xperia Z2     |      yes      |                                           | Harald Pedersen  |
| LG G2              |      yes      |                                           | @michaelossmann  |
| LG G3              |      yes      |                                           | @HashHacks       |
| Moto G 4G          |      yes      |                                           | @paul2dart       |
| Motorola Xoom M601 |      yes      |                                           | Reg Blank        |
| Dragon Touch A1X   |      yes      |                                           | John Wright      |
| Moto X  (1st gen)  |      yes      |                                           | sabas1080        |


Known Issues
------------
* USB connection is too slow for Sample Rates >15 Msps (testet on Nexus 7)
* FileWriter in example app is too slow. Only works for ~ 2 Msps on old devices.
* It seems that the HackRF sometimes gets to less power if using a to long or low
  quality USB cable.


Installation / Usage
--------------------
Build the library and the example app by using the Android Studio projects:
* hackrf_android/
* hackrf_android/examples/

If you want to use the library in your own app, just copy the 
hackrf_android/app/build/outputs/aar/app-debug.aar file into your project and 
include it as a library. See the example project to learn how to use the library.

The hackrf_android.aar and the HackRF_Test.apk files are also in this repository
so that they can be used without building them. But they won't be synched to the
latest code base all the time.

A short howto to get started can be found on this blog post:
[http://tech.mantz-it.com/2014/10/hackrfandroid-using-hackrf-with-android.html]
(http://tech.mantz-it.com/2014/10/hackrfandroid-using-hackrf-with-android.html)

Use the example application to test the library on your device and trouble shoot
any problems. It has the option to show the logcat output!

License
-------
This library is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public
License as published by the Free Software Foundation; either
version 2 of the License, or (at your option) any later version.
[http://www.gnu.org/licenses/gpl.html](http://www.gnu.org/licenses/gpl.html) GPL version 2 or higher

principal author: Dennis Mantz <dennis.mantzgooglemail.com>

principal author of libhackrf: Michael Ossmann <mikeossmann.com>
