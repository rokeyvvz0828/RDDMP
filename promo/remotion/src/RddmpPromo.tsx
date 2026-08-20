import React from 'react';
import {
  AbsoluteFill,
  Audio,
  Img,
  Sequence,
  interpolate,
  staticFile,
  useCurrentFrame,
} from 'remotion';

export const FPS = 30;
export const TOTAL_FRAMES = 1085;

type PromoProps = { bgm?: boolean };
type Shot = {
  from: number;
  duration: number;
  image: string;
  number: string;
  eyebrow: string;
  title: string;
  body: string;
  accent: string;
};

const SHOTS: Shot[] = [
  {
    from: 245,
    duration: 190,
    image: 'assets/project-overview.png',
    number: '01',
    eyebrow: '项目工作台',
    title: '从目标到进度，一眼看清',
    body: '项目概览、成员、角色和日历放在同一张工作台上，让交付状态持续可见。',
    accent: '#c64b3c',
  },
  {
    from: 490,
    duration: 190,
    image: 'assets/project-plans.png',
    number: '02',
    eyebrow: '项目计划',
    title: '主计划拆解，执行有章法',
    body: '主计划、子计划、负责人和配合方形成清晰层级，计划进度随项目推进更新。',
    accent: '#3568a8',
  },
  {
    from: 735,
    duration: 190,
    image: 'assets/workflow.png',
    number: '03',
    eyebrow: '流程审批',
    title: '把规则画出来，让审批流起来',
    body: '用 BPMN 思路设计用户任务、条件网关和审批连线，过程与结果都能追溯。',
    accent: '#2f806c',
  },
  {
    from: 980,
    duration: 50,
    image: 'assets/ai.png',
    number: '04',
    eyebrow: '平台底座',
    title: '组织、权限与能力持续扩展',
    body: '以统一的组织、角色、菜单和组件能力，承接后续业务与 AI 接入。',
    accent: '#b8832f',
  },
];

const clamp = (value: number) => Math.min(1, Math.max(0, value));
const ease = (value: number) => 1 - Math.pow(1 - clamp(value), 3);

const Paper: React.FC<{ children: React.ReactNode; className?: string }> = ({ children, className = '' }) => (
  <AbsoluteFill className={`paper ${className}`}>
    <div className="paper__grain" />
    <div className="paper__rules" />
    {children}
  </AbsoluteFill>
);

const Brand: React.FC<{ compact?: boolean }> = ({ compact = false }) => (
  <div className={`brand ${compact ? 'brand--compact' : ''}`}>
    <span className="brand__seal">R</span>
    <span>
      <strong>RDDMP</strong>
      {!compact && <small>研发交付管理平台</small>}
    </span>
  </div>
);

const TitleCard: React.FC<{ title: string; sub: string; accent: string }> = ({ title, sub, accent }) => {
  const frame = useCurrentFrame();
  const reveal = ease(interpolate(frame, [0, 22], [0, 1], { extrapolateLeft: 'clamp', extrapolateRight: 'clamp' }));
  return (
    <Paper className="title-card">
      <div className="title-card__mark" style={{ backgroundColor: accent }} />
      <div style={{ opacity: reveal, transform: `translateY(${(1 - reveal) * 18}px)` }}>
        <div className="title-card__kicker">RDDMP / 研发交付管理平台</div>
        <h1>{title}</h1>
        <p>{sub}</p>
      </div>
      <div className="title-card__stamp">统一 · 协同 · 可追溯</div>
    </Paper>
  );
};

const Opening: React.FC = () => {
  const frame = useCurrentFrame();
  const reveal = ease(interpolate(frame, [0, 70], [0, 1], { extrapolateLeft: 'clamp', extrapolateRight: 'clamp' }));
  const seal = interpolate(frame, [0, 95], [0.65, 1], { extrapolateLeft: 'clamp', extrapolateRight: 'clamp' });
  return (
    <Paper className="opening">
      <div className="opening__ink" style={{ transform: `scale(${seal}) rotate(${frame * 0.035}deg)` }} />
      <div className="opening__content" style={{ opacity: reveal, transform: `translateY(${(1 - reveal) * 26}px)` }}>
        <Brand />
        <div className="opening__rule" />
        <div className="opening__kicker">工程交付 · 单租户平台</div>
        <h1>让复杂交付<br /><em>清晰可控</em></h1>
        <p>组织、项目、流程与平台能力，沉淀为一套可持续扩展的工作方式。</p>
      </div>
      <div className="opening__side">PRODUCT FILM / 2026</div>
    </Paper>
  );
};

const ProductShot: React.FC<{ shot: Shot }> = ({ shot }) => {
  const frame = useCurrentFrame();
  const progress = frame / Math.max(1, shot.duration - 1);
  const enter = ease(interpolate(progress, [0, 0.18], [0, 1], { extrapolateLeft: 'clamp', extrapolateRight: 'clamp' }));
  const settle = interpolate(progress, [0.12, 0.62], [0, 1], { extrapolateLeft: 'clamp', extrapolateRight: 'clamp' });
  const x = interpolate(progress, [0, 0.45, 1], [60, 0, -16], { extrapolateLeft: 'clamp', extrapolateRight: 'clamp' });
  const y = interpolate(progress, [0, 0.55, 1], [34, 0, -10], { extrapolateLeft: 'clamp', extrapolateRight: 'clamp' });
  const copyIn = ease(interpolate(progress, [0.16, 0.38], [0, 1], { extrapolateLeft: 'clamp', extrapolateRight: 'clamp' }));
  const zoom = interpolate(progress, [0, 1], [1.08, 1.01], { extrapolateLeft: 'clamp', extrapolateRight: 'clamp' });
  return (
    <Paper className="product-shot">
      <div className="product-shot__top"><Brand compact /><span>RDDMP / 产品实景</span></div>
      <div className="product-shot__copy" style={{ opacity: copyIn, transform: `translateY(${(1 - copyIn) * 20}px)` }}>
        <div className="shot-eyebrow"><i style={{ backgroundColor: shot.accent }} />{shot.eyebrow}</div>
        <h1>{shot.title}</h1>
        <p>{shot.body}</p>
        <div className="shot-line" style={{ backgroundColor: shot.accent }} />
        <div className="shot-meta">{shot.number} / 04&nbsp;&nbsp;真实页面 · 演示数据</div>
      </div>
      <div className="product-shot__image-wrap" style={{ opacity: enter, transform: `translate3d(${x * (1 - settle)}px, ${y * (1 - settle)}px, 0)` }}>
        <div className="product-shot__tape" style={{ backgroundColor: shot.accent }} />
        <div className="product-shot__image" style={{ transform: `perspective(1500px) rotateX(${(1 - settle) * 4}deg) rotateY(${(1 - settle) * -4}deg) scale(${zoom})` }}>
          <Img src={staticFile(shot.image)} />
          <div className="product-shot__sheen" style={{ transform: `translateX(${interpolate(progress, [0.18, 0.8], [-120, 120], { extrapolateLeft: 'clamp', extrapolateRight: 'clamp' })}%)` }} />
        </div>
      </div>
      <div className="product-shot__number" style={{ color: shot.accent }}>{shot.number}</div>
      <div className="product-shot__footer"><span>RDDMP · 研发交付管理平台</span><span>{shot.eyebrow}</span></div>
    </Paper>
  );
};

const Closing: React.FC = () => {
  const frame = useCurrentFrame();
  const reveal = ease(interpolate(frame, [0, 24], [0, 1], { extrapolateLeft: 'clamp', extrapolateRight: 'clamp' }));
  return (
    <Paper className="closing">
      <div className="closing__line closing__line--one" />
      <div className="closing__line closing__line--two" />
      <div className="closing__content" style={{ opacity: reveal, transform: `translateY(${(1 - reveal) * 18}px)` }}>
        <Brand />
        <div className="closing__rule" />
        <h1>每一次交付，<em>都有迹可循</em></h1>
        <p>RDDMP · 研发交付管理平台</p>
      </div>
      <div className="closing__footer">组织协同 · 项目计划 · 流程审批 · 平台扩展</div>
    </Paper>
  );
};

const SFX = [
  { from: 8, volume: 0.22 },
  { from: 240, volume: 0.18 },
  { from: 485, volume: 0.18 },
  { from: 730, volume: 0.18 },
  { from: 975, volume: 0.14 },
  { from: 1038, volume: 0.2 },
];

export const RddmpPromo: React.FC<PromoProps> = ({ bgm = false }) => (
  <AbsoluteFill className="promo">
    <style>{STYLES}</style>
    {bgm && <Audio src={staticFile('audio/bgm-tech-house.mp3')} volume={0.16} />}
    {SFX.map((item, index) => (
      <Sequence key={item.from} from={item.from} durationInFrames={90}>
        <Audio src={staticFile(index === SFX.length - 1 ? 'audio/sfx/sparkle.mp3' : index === 0 ? 'audio/sfx/impact-transition.mp3' : 'audio/sfx/whoosh-electric.mp3')} volume={item.volume} />
      </Sequence>
    ))}
    <Sequence from={0} durationInFrames={190}><Opening /></Sequence>
    <Sequence from={190} durationInFrames={55}><TitleCard title="统一研发交付平台" sub="把组织协同、项目推进与流程审批，放进同一套工作方式。" accent="#c64b3c" /></Sequence>
    {SHOTS.map((shot) => <Sequence key={shot.number} from={shot.from} durationInFrames={shot.duration}><ProductShot shot={shot} /></Sequence>)}
    <Sequence from={435} durationInFrames={55}><TitleCard title="计划有序，责任清晰" sub="从主计划到每一项任务，进度与责任始终连在一起。" accent="#3568a8" /></Sequence>
    <Sequence from={680} durationInFrames={55}><TitleCard title="流程审批，按图推进" sub="审批路径可配置，节点状态可追踪，过程结果可复盘。" accent="#2f806c" /></Sequence>
    <Sequence from={925} durationInFrames={55}><TitleCard title="为持续扩展而生" sub="统一的权限、组件与接入规范，承接下一项业务能力。" accent="#b8832f" /></Sequence>
    <Sequence from={1030} durationInFrames={55}><Closing /></Sequence>
  </AbsoluteFill>
);

export const RddmpPromoV2 = RddmpPromo;

const STYLES = `
  .promo { background: #ede8de; color: #252321; font-family: "Microsoft YaHei", "Noto Sans SC", Arial, sans-serif; overflow: hidden; }
  .paper { background: #ede8de; overflow: hidden; }
  .paper__grain { position: absolute; inset: 0; opacity: .4; background-image: radial-gradient(rgba(47, 39, 29, .13) .7px, transparent .8px), radial-gradient(rgba(255,255,255,.45) .7px, transparent .8px); background-size: 7px 7px, 11px 11px; background-position: 0 0, 4px 5px; mix-blend-mode: multiply; }
  .paper__rules { position: absolute; inset: 0; opacity: .25; background-image: linear-gradient(90deg, transparent 0 78px, rgba(168, 76, 54, .24) 78px 80px, transparent 80px), repeating-linear-gradient(0deg, transparent 0 63px, rgba(58, 81, 109, .08) 64px); }
  .brand { display: flex; align-items: center; gap: 14px; color: #252321; letter-spacing: .04em; }
  .brand__seal { display: grid; place-items: center; width: 50px; height: 50px; color: #f8f1e6; background: #b74336; border-radius: 50%; font-family: Georgia, serif; font-size: 27px; font-weight: 700; box-shadow: 3px 4px 0 rgba(97, 54, 38, .24); }
  .brand strong { display: block; font-family: Georgia, serif; font-size: 25px; letter-spacing: .16em; }
  .brand small { display: block; margin-top: 4px; color: #766c60; font-size: 12px; letter-spacing: .13em; }
  .brand--compact { gap: 8px; }
  .brand--compact .brand__seal { width: 30px; height: 30px; font-size: 16px; box-shadow: 2px 2px 0 rgba(97, 54, 38, .18); }
  .brand--compact strong { font-size: 15px; }
  .opening__content { position: absolute; left: 190px; top: 260px; width: 760px; z-index: 2; }
  .opening__rule { width: 330px; height: 4px; margin: 42px 0 30px; background: #b74336; transform: rotate(-1deg); }
  .opening__kicker, .title-card__kicker { color: #3568a8; font-size: 17px; font-weight: 700; letter-spacing: .18em; }
  .opening h1 { margin: 25px 0 18px; font-family: Georgia, "Microsoft YaHei", serif; font-size: 74px; line-height: 1.12; letter-spacing: .04em; }
  .opening h1 em { color: #b74336; font-style: normal; }
  .opening p { max-width: 620px; margin: 0; color: #756b5d; font-size: 22px; line-height: 1.8; letter-spacing: .08em; }
  .opening__ink { position: absolute; right: 175px; top: 180px; width: 520px; height: 520px; border: 2px solid rgba(53, 104, 168, .4); border-radius: 48% 52% 45% 55%; box-shadow: inset 0 0 0 22px rgba(53,104,168,.05), 0 0 0 12px rgba(183,67,54,.06); }
  .opening__ink::before, .opening__ink::after { content: ""; position: absolute; inset: 10%; border: 1px solid rgba(183,67,54,.38); border-radius: 55% 45% 52% 48%; transform: rotate(38deg); }
  .opening__ink::after { inset: 18%; transform: rotate(-35deg); border-color: rgba(47,128,108,.3); }
  .opening__side { position: absolute; right: 100px; bottom: 75px; color: #8d8275; font: 12px Consolas, monospace; letter-spacing: .2em; writing-mode: vertical-rl; }
  .title-card { display: grid; place-items: center; text-align: center; }
  .title-card > div:nth-child(2) { position: relative; z-index: 1; }
  .title-card__mark { position: absolute; top: 185px; left: 50%; width: 170px; height: 7px; transform: translateX(-50%) rotate(-2deg); opacity: .8; }
  .title-card h1 { margin: 24px 0 16px; font-family: Georgia, "Microsoft YaHei", serif; font-size: 68px; letter-spacing: .08em; }
  .title-card p { margin: 0; color: #74695d; font-size: 22px; letter-spacing: .1em; }
  .title-card__stamp { position: absolute; right: 100px; bottom: 82px; color: #8d8275; font-size: 13px; letter-spacing: .2em; transform: rotate(-4deg); }
  .product-shot__top { position: absolute; z-index: 3; left: 80px; right: 80px; top: 52px; display: flex; align-items: center; justify-content: space-between; color: #756b5d; font-size: 13px; letter-spacing: .14em; }
  .product-shot__copy { position: absolute; z-index: 2; left: 100px; top: 285px; width: 485px; }
  .shot-eyebrow { display: flex; align-items: center; gap: 10px; color: #756b5d; font-size: 18px; letter-spacing: .14em; }
  .shot-eyebrow i { width: 9px; height: 9px; border-radius: 50%; display: block; }
  .product-shot__copy h1 { margin: 25px 0 18px; font-family: Georgia, "Microsoft YaHei", serif; font-size: 52px; line-height: 1.2; letter-spacing: .03em; }
  .product-shot__copy p { margin: 0; color: #756b5d; font-size: 19px; line-height: 1.85; }
  .shot-line { width: 72px; height: 4px; margin-top: 32px; transform: rotate(-2deg); }
  .shot-meta { margin-top: 14px; color: #958a7c; font: 12px Consolas, monospace; letter-spacing: .1em; }
  .product-shot__image-wrap { position: absolute; top: 155px; right: 70px; width: 1095px; height: 755px; }
  .product-shot__tape { position: absolute; z-index: 3; left: 38%; top: -20px; width: 160px; height: 42px; opacity: .76; transform: rotate(3deg); }
  .product-shot__image { position: absolute; inset: 0; overflow: hidden; border: 9px solid #f7f2e8; box-shadow: 8px 11px 0 rgba(93, 72, 56, .2), 0 18px 35px rgba(91, 68, 49, .18); transform-origin: center; }
  .product-shot__image img { width: 100%; height: 100%; object-fit: cover; object-position: top left; display: block; }
  .product-shot__sheen { position: absolute; top: -15%; left: -30%; width: 20%; height: 130%; background: rgba(255,255,255,.38); transform: skewX(-14deg); }
  .product-shot__number { position: absolute; left: 82px; bottom: 115px; font: 132px/1 Georgia, serif; font-weight: 700; opacity: .15; }
  .product-shot__footer { position: absolute; left: 80px; right: 80px; bottom: 54px; display: flex; justify-content: space-between; color: #958a7c; font: 12px Consolas, monospace; letter-spacing: .12em; }
  .closing__content { position: absolute; left: 200px; top: 330px; }
  .closing__rule { width: 360px; height: 4px; margin: 38px 0 28px; background: #b74336; }
  .closing h1 { margin: 0; font-family: Georgia, "Microsoft YaHei", serif; font-size: 64px; letter-spacing: .06em; }
  .closing h1 em { color: #3568a8; font-style: normal; }
  .closing p { margin-top: 24px; color: #756b5d; font-size: 19px; letter-spacing: .15em; }
  .closing__line { position: absolute; left: 0; width: 100%; height: 2px; background: #3568a8; opacity: .24; transform-origin: left; }
  .closing__line--one { top: 250px; transform: rotate(13deg); }
  .closing__line--two { bottom: 245px; transform: rotate(-13deg); background: #b74336; }
  .closing__footer { position: absolute; left: 200px; right: 200px; bottom: 70px; color: #8d8275; font-size: 13px; letter-spacing: .16em; }
`;
