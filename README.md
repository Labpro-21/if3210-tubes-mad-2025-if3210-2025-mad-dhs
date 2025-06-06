# Purrytify - Android Music Streaming App

## Description
Purrytify is a music streaming application for Android that allows users to listen to their favorite music. The app was developed as part of a mobile app development course assignment. In a world without music, this app aims to bring music back to people's lives with features like music playback, library management, and user profiles.

Key features include:
- User authentication (login/logout)
- Music playback with player controls (play, pause, next, previous, queue, repeat)
- Library of songs with browsing capability
- User profile
- Network connectivity monitoring
- Token-based authentication with expiration handling
- Online Songs and Download Features
- Audio playback in notification controls
- Audio output switching
- Edit profile
- User analytics (sound capsule)
- Song recommendations

## Libraries Used

### Core Android
- **AndroidX Core KTX** (1.12.0) - Kotlin extensions for Android core functionality
- **AndroidX AppCompat** (1.6.1) - Backward compatibility for newer Android features
- **Material Components** (1.9.0) - Material Design UI components
- **ConstraintLayout** (2.1.4) - Flexible layout manager
- **Core Splashscreen** (1.0.1) - API for splash screen implementation
- **Card View**	(1.0.0) - CardView UI component
- **Recycler View**	(1.3.2) - RecyclerView for dynamic lists

### Architecture Components
- **ViewModel KTX** (2.6.1) - Lifecycle-aware data holders
- **LiveData KTX** (2.6.1) - Observable data holder that respects lifecycle
- **Room** (2.6.1) - SQLite abstraction layer for local database
- **Navigation Component** (2.6.0) - Navigation between fragments
- **Palette KTX** (1.0.0) - Color palette extraction from images

### Networking & API
- **Retrofit2** (2.9.0) - Type-safe HTTP client for API calls
- **OkHttp3** (4.10.0) - HTTP client for network requests
- **OkHttp Logging Interceptor** (4.10.0) - Network request logging
- **Gson** (2.10.1) - JSON serialization/deserialization

### Image Loading
- **Glide** (4.14.2) - Image loading and caching

### Concurrency
- **Kotlinx Coroutines** (1.7.3) - Asynchronous programming

### Maps and Location
- **Maps** (18.2.0) - Google Maps SDK
- **Location** (21.0.1) - Location APIs
- 
### Data Visualization
- **MPAndroidChart** (3.1.0) - Charting and graphing library

### Firebase Dynamic Links
- **Firebase Dynamic Links** (21.0.1) - Create and handle Firebase Dynamic Links

### Security
- **Security-Crypto** (1.1.0-alpha03) - Encrypted SharedPreferences

### Testing
- **JUnit** (4.13.2) - Unit testing framework
- **AndroidX Test JUnit** (1.1.5) - JUnit extensions for Android
- **Espresso** (3.5.1) - UI testing framework



## Screenshots
### Splash Screen
![Splash Screen](screenshots/splash_screen.png)

### Login Screen
![Login Screen](screenshots/login_screen.png)
![Login_Screen_Landscape](screenshots/login_landscape.png)

### Home Screen
![Home Screen](screenshots/home_new.jpg)
![Home_Screen_Landscape](screenshots/home_landscape.png)


### Library Screen
![Library Screen 1](screenshots/library_all.png)
![Library Screen 2](screenshots/library_liked.png)
![Library_Screen_Landscape](screenshots/library_landscape.png)

### Now Playing Screen
![Now Playing Screen 1](screenshots/player.png)
![Now Playing Screen 2](screenshots/shuffle.png)
![Now Playing Screen 3](screenshots/repeat_all.png)
![Now Playing Screen 4](screenshots/repeat_one.png)
![Now Playing Screen 5](screenshots/liked.png)
![Song_Landscape](screenshots/song_landscape.png)

### Song Management
![Song Management Screen 1](screenshots/add_song.png)
![Song Management Screen  2](screenshots/edit_song.png)
![Song Management Screen 3](screenshots/delete_song.png)

### Profile Screen
![Profile Screen](screenshots/profile.png)
![Profile_Screen_Landscape](screenshots/profile_landscape.png)


### No Network Connection
![No Network Screen 1](screenshots/no_network1.png)
![No Network Screen 2](screenshots/no_network2.png)
![No Network Screen 3](screenshots/no_network3.png)
![No Network Screen 4](screenshots/no_network4.png)

### Queue (swiped left to add to queue)
![Queue](screenshots/queue.png)

### Chart
![Top_Global Screen](screenshots/top_global_chart.png)
![Top_Indonesia Screen](screenshots/top_indonesia_chart.png)
![Chart_Screen_Landscape](screenshots/chart_landscape.png)

### Share
![Share_Link Screen](screenshots/share_link.png)
![Share QR_Screen](screenshots/share_qr.png)
![QR_scan](screenshots/qrscan.jpg)

### Recommendation
![Discover_Mix Screen](screenshots/recommendation_discover_mix.png)
![Liked_Mix Screen](screenshots/recommendation_liked_mix.png)
![On_Repeat](screenshots/recommendation_on_repeat.png)
![Recommendation_Landscape](screenshots/recommendation_landscape.png)

### Sound Capsule
![SoundCapsule Screen](screenshots/soundcapsule.jpg)
![Monthly_SoundCapsule Screen](screenshots/soundcapsule_may.jpg)
![Time_Listened](screenshots/time_listened.jpg)
![Top_Artists](screenshots/topartists.jpg)
![Top_Songs](screenshots/topsongs.jpg)
![SoundCapsule_Landscape](screenshots/sound_capsule_landscape.png)
![SoundCapsule1_Landscape](screenshots/time_listened_landscape.png)
![Export1](screenshots/export.jpg)
![Export2](screenshots/export_all.jpg)
![Export3](screenshots/export_may.jpg)

### Audio Routing
![AudioRouting1 Screen](screenshots/audio_output_internal_speaker.jpg)
![AudioRouting2 Screen](screenshots/audio_output_speaker.jpg)

### Notification
![Notification Screen](screenshots/notif.png)


## Team Members and Division of Work

| Name                             | NIM          | Contributions                                                                                                                                                           | Hours Spent                                       |
|----------------------------------|--------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------|
| Shazya Audrea Taufik             | 13522063     | Authentication (Login/Logout), User Profile, Network Sensing, Background Service, Navbar, Search, Sound Capsule, Recommendation, Edit Profile, Audio Routing            | Preparation: 10 hours<br>Implementation: 60 hours |
| Zahira Dina Amalia               | 13522085     | Music Playback (Play, Pause, Next, Prev, Shuffle), Add Song, Queue, Repeat, Home, Library (All Songs), Online Songs, Download Songs, Share Link, Share QR, Notification | Preparation: 10 hours<br>Implementation: 60 hours |
| Ellijah Darrellshane Suryanegara | 13522097     | Music Playback (Play, Pause), Edit and Delete Song, Home, Library (Liked Songs), Notification, Audio Routing                                                            | Preparation: 10 hours<br>Implementation: 60 hours |

## Setup Instructions
1. Clone the repository
2. Open the project in Android Studio
3. Sync Gradle files
4. Run the app on an emulator or physical device
5. Login with your credentials
    - For testing: Use your ITB email and NIM
    - Example: "13522XXX@std.stei.itb.ac.id" / "13522XXX"

