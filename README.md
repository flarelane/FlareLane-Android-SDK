# FlareLane-Android-SDK

## Region

Each FlareLane project lives in one region. Pass the project's region when initializing:

```java
// Projects in the default (US) region: unchanged, the region can be omitted.
FlareLane.initWithContext(this, "YOUR_PROJECT_ID", false);

// Projects created in the Seoul region.
FlareLane.initWithContext(this, "YOUR_PROJECT_ID", "kr", false);
```

`region` defaults to `"us"`. Use the region your project was created in. Each region only knows its own projects, so any other region refuses the SDK's requests. An invalid value (empty or malformed) stops initialization and logs an error.
