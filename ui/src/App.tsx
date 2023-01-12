import type { Component } from 'solid-js';
import Comp from './Comp';
import RemoteConnection from "./RemoteConnection";

const App: Component = () => {
  return (
    <>
      <RemoteConnection/>
      <Comp />
    </>
  );
};

export default App;
