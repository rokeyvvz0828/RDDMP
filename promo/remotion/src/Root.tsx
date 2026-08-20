import React from 'react';
import { Composition } from 'remotion';
import { RddmpPromo, RddmpPromoV2, FPS, TOTAL_FRAMES } from './RddmpPromo';

export const RemotionRoot: React.FC = () => (
  <>
    <Composition
      id="RddmpPromo"
      component={RddmpPromo}
      durationInFrames={TOTAL_FRAMES}
      fps={FPS}
      width={1920}
      height={1080}
      defaultProps={{ bgm: false }}
    />
    <Composition
      id="RddmpPromoV2"
      component={RddmpPromoV2}
      durationInFrames={TOTAL_FRAMES}
      fps={FPS}
      width={1920}
      height={1080}
      defaultProps={{ bgm: false }}
    />
  </>
);
