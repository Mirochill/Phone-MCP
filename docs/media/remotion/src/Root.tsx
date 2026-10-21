import {Composition, Folder} from 'remotion';
import {Film} from './Film';

export const RemotionRoot: React.FC = () => {
  return (
    <>
      <Composition id="PhoneMCP-Launch" component={Film} durationInFrames={660} fps={30} width={1920} height={1080} defaultProps={{offset:0,withAudio:true}}/>
      <Folder name="Review-chapters">
        <Composition id="Command" component={Film} durationInFrames={150} fps={30} width={1920} height={1080} defaultProps={{offset:0,withAudio:true}}/>
        <Composition id="Control" component={Film} durationInFrames={270} fps={30} width={1920} height={1080} defaultProps={{offset:150,withAudio:true}}/>
        <Composition id="Ecosystem" component={Film} durationInFrames={240} fps={30} width={1920} height={1080} defaultProps={{offset:420,withAudio:true}}/>
      </Folder>
    </>
  );
};
