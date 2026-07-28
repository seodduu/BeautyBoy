import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { Footer } from './Footer';

function renderAt(path: string) {
  render(
    <MemoryRouter initialEntries={[path]}>
      <Footer />
    </MemoryRouter>,
  );
}

describe('Footer', () => {
  it('contentinfo 랜드마크로 렌더되고 데모 고지가 있다', () => {
    renderAt('/main');
    const footer = screen.getByRole('contentinfo');
    expect(footer).toHaveTextContent('본 사이트는 취업 포트폴리오용 데모입니다');
    expect(footer).toHaveTextContent('© 2026 BeautyBoy — Portfolio Demo');
  });

  it('전자상거래 표기 항목이 형식 예시 값으로 존재한다', () => {
    renderAt('/main');
    const footer = screen.getByRole('contentinfo');
    expect(footer).toHaveTextContent('사업자등록번호 000-00-00000');
    expect(footer).toHaveTextContent('통신판매업 신고번호');
  });

  it('약관·개인정보처리방침은 대상 화면이 없으므로 링크가 아니다', () => {
    renderAt('/main');
    expect(screen.queryByRole('link', { name: '이용약관' })).not.toBeInTheDocument();
    expect(screen.getByText('이용약관')).toBeInTheDocument();
  });

  it('랜딩(/)에서는 렌더하지 않는다', () => {
    renderAt('/');
    expect(screen.queryByRole('contentinfo')).not.toBeInTheDocument();
  });
});
