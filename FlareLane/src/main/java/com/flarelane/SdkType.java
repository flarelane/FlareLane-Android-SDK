package com.flarelane;

enum SdkType {
    NATIVE {
        @Override
        public String toString() {
            return "native";
        }
    },
    REACTNATIVE {
        @Override
        public String toString() {
            return "reactnative";
        }
    },
    FLUTTER {
        @Override
        public String toString() {
            return "flutter";
        }
    },
}
