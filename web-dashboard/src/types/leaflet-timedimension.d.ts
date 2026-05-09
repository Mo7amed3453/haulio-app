// Minimal TypeScript declarations for leaflet-timedimension
// Full docs: https://github.com/socib/Leaflet.TimeDimension

import 'leaflet';

declare module 'leaflet' {
  interface TimeDimensionOptions {
    times?: string | number[];
    timeInterval?: string;
    period?: string;
    validTimeRange?: string;
    currentTime?: number;
    loadingTimeout?: number;
    lowerLimitTime?: number;
    upperLimitTime?: number;
  }

  class TimeDimension extends Evented {
    constructor(options?: TimeDimensionOptions);
    getCurrentTime(): number;
    setCurrentTime(time: number): void;
    nextTime(numSteps?: number, loop?: boolean): void;
    previousTime(numSteps?: number, loop?: boolean): void;
    getAvailableTimes(): number[];
    isLoading(): boolean;
    prepareNextTimes(numSteps: number, howmany: number): void;
    registerSyncedLayer(layer: Layer): void;
    unregisterSyncedLayer(layer: Layer): void;
  }

  function timeDimension(options?: TimeDimensionOptions): TimeDimension;

  namespace Control {
    interface TimeDimensionOptions extends ControlOptions {
      timeDimension?: TimeDimension;
      player?: TimeDimensionPlayer;
      speedSlider?: boolean;
      minSpeed?: number;
      maxSpeed?: number;
      speedStep?: number;
      timeSliderDragUpdate?: boolean;
      limitSliders?: boolean;
      limitMinimumRange?: number;
      loopButton?: boolean;
      backwardButton?: boolean;
      forwardButton?: boolean;
      playReverseButton?: boolean;
      displayDate?: boolean;
      timeZones?: string[];
      playerOptions?: Record<string, unknown>;
      autoPlay?: boolean;
    }

    class TimeDimension extends Control {
      constructor(options?: TimeDimensionOptions);
    }
  }

  interface TimeDimensionPlayer {
    start(numSteps?: number): void;
    stop(): void;
    getRunning(): boolean;
  }

  interface Map {
    timeDimension: TimeDimension;
  }
}
