import React from 'react';
import { Tabs } from 'expo-router';

// Single-screen performance app — no tab bar, no header.
export default function TabLayout() {
  return (
    <Tabs
      screenOptions={{
        headerShown: false,
        tabBarStyle: { display: 'none' },
      }}
    >
      <Tabs.Screen name="index" options={{ title: 'MouthMIDI' }} />
    </Tabs>
  );
}
