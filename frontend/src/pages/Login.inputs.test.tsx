import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { Login } from './Login';
import { Signup } from './Signup';

describe('Login — 입력 타입/키보드 계약', () => {
  it('이메일 입력이 type=email, inputmode=email, autocomplete=email 을 갖는다', () => {
    render(
      <MemoryRouter>
        <Login />
      </MemoryRouter>,
    );

    const input = screen.getByLabelText('이메일');
    expect(input).toHaveAttribute('type', 'email');
    expect(input).toHaveAttribute('inputmode', 'email');
    expect(input).toHaveAttribute('autocomplete', 'email');
  });

  it('비밀번호 입력이 type=password, autocomplete=current-password 를 갖는다', () => {
    render(
      <MemoryRouter>
        <Login />
      </MemoryRouter>,
    );

    const input = screen.getByLabelText('비밀번호');
    expect(input).toHaveAttribute('type', 'password');
    expect(input).toHaveAttribute('autocomplete', 'current-password');
  });
});

describe('Signup — 입력 타입/키보드 계약', () => {
  it('이메일 입력이 type=email, inputmode=email, autocomplete=email 을 갖는다', () => {
    render(
      <MemoryRouter>
        <Signup />
      </MemoryRouter>,
    );

    const input = screen.getByLabelText('이메일');
    expect(input).toHaveAttribute('type', 'email');
    expect(input).toHaveAttribute('inputmode', 'email');
    expect(input).toHaveAttribute('autocomplete', 'email');
  });

  it('비밀번호 입력이 type=password, autocomplete=new-password 를 갖는다 (inputMode는 부여하지 않는다)', () => {
    render(
      <MemoryRouter>
        <Signup />
      </MemoryRouter>,
    );

    const input = screen.getByLabelText('비밀번호');
    expect(input).toHaveAttribute('type', 'password');
    expect(input).toHaveAttribute('autocomplete', 'new-password');
    expect(input).not.toHaveAttribute('inputmode');
  });
});
