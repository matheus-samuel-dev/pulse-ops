import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { getContrastRatio, useTheme } from '@mui/material/styles';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { PulseOpsThemeProvider, useColorMode } from './PulseOpsThemeProvider';
function InspectTheme(){
  const {palette}=useTheme();const {toggleColorMode}=useColorMode();
  const colors=['primary','secondary','success','warning','error','info'] as const;
  return <><output aria-label="Tema atual">{palette.mode}</output><output aria-label="Contrastes">{JSON.stringify([...colors.map(color=>getContrastRatio(palette[color].main,palette[color].contrastText)),getContrastRatio(palette.text.primary,palette.background.paper),getContrastRatio(palette.text.secondary,palette.background.paper)])}</output><button onClick={toggleColorMode}>Alternar tema</button></>;
}
describe('Aparência acessível',()=>{
  beforeEach(()=>localStorage.clear());afterEach(cleanup);
  it.each(['dark','light'])('mantém contraste de texto de pelo menos 4,5:1 no tema %s',mode=>{
    localStorage.setItem('pulseops.color-mode',mode);render(<PulseOpsThemeProvider><InspectTheme/></PulseOpsThemeProvider>);
    const ratios=JSON.parse(screen.getByLabelText('Contrastes').textContent!) as number[];
    for(const ratio of ratios)expect(ratio).toBeGreaterThanOrEqual(4.5);
  });
  it('persiste a preferência e restaura ao abrir a interface novamente',async()=>{
    const view=render(<PulseOpsThemeProvider><InspectTheme/></PulseOpsThemeProvider>);
    await userEvent.click(screen.getByRole('button',{name:'Alternar tema'}));expect(screen.getByLabelText('Tema atual')).toHaveTextContent('light');
    view.unmount();render(<PulseOpsThemeProvider><InspectTheme/></PulseOpsThemeProvider>);expect(screen.getByLabelText('Tema atual')).toHaveTextContent('light');
  });
});
