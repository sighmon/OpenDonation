# OpenDonation
An Open Source donation app for Android making use of the Square card reader.

## Secrets
Secrets are stored in a file you’ll need to create:

```XML
<!-- Inside of `app/src/main/res/values/secrets.xml` -->
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="square_client_id">YOUR_CLIENT_ID</string>
</resources>
```

Usage:

```Java
getString(R.string.square_client_id);
```

```XML
<meta-data
    android:name="com.squareup"
    android:value="@string/square_client_id"/>
```

Follow the guide to get YOUR_CLIENT_ID: <https://docs.connect.squareup.com/articles/point-of-sale-api-android>

### Credits
Idea by Ashleigh Hull.