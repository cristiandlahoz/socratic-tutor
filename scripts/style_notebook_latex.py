from __future__ import annotations

import sys
from pathlib import Path


STYLE_BLOCK = r"""
\geometry{tmargin=0.9in,bmargin=1.0in,lmargin=0.95in,rmargin=0.95in}
\IfFontExistsTF{Georgia}{\setmainfont{Georgia}}{\setmainfont{TeX Gyre Pagella}}
\IfFontExistsTF{Helvetica Neue}{\setsansfont{Helvetica Neue}}{\setsansfont{Arial}}
\IfFontExistsTF{Menlo}{\setmonofont{Menlo}[Scale=0.92]}{\setmonofont{Courier New}[Scale=0.92]}
\tcbuselibrary{skins,breakable}
\setlength{\droptitle}{-2.5em}
\pretitle{\begin{flushleft}\Huge\bfseries}
\posttitle{\par\end{flushleft}\vspace{-0.5em}}
\preauthor{}
\postauthor{}
\predate{}
\postdate{}
\date{}
\definecolor{titlecolor}{HTML}{1F2937}
\definecolor{bodytext}{HTML}{273444}
\definecolor{mutedtext}{HTML}{5B6573}
\definecolor{linkcolor}{HTML}{0F5E9C}
\definecolor{urlcolor}{HTML}{0F5E9C}
\definecolor{citecolor}{HTML}{0F5E9C}
\definecolor{incolor}{HTML}{6D28D9}
\definecolor{outcolor}{HTML}{9A3412}
\definecolor{cellborder}{HTML}{D7D2C8}
\definecolor{cellbackground}{HTML}{F8F5EF}
\definecolor{codecomment}{HTML}{667085}
\definecolor{codestring}{HTML}{0F766E}
\definecolor{codekeyword}{HTML}{7C3AED}
\definecolor{codefunction}{HTML}{0F4C81}
\definecolor{codenumber}{HTML}{9A3412}
\color{bodytext}
\hypersetup{
  breaklinks=true,
  colorlinks=true,
  urlcolor=urlcolor,
  linkcolor=linkcolor,
  citecolor=citecolor,
  pdftitle={Qwen3-4B-Instruct LoRA Fine-Tuning}
}
\setlength{\parskip}{0.55em}
\setlength{\parindent}{0pt}
\fvset{fontsize=\small}
\renewcommand{\arraystretch}{1.15}
\tcbset{
  enhanced,
  breakable,
  boxrule=0.6pt,
  arc=1.8mm,
  left=3mm,
  right=3mm,
  top=2mm,
  bottom=2mm,
  colback=cellbackground,
  colframe=cellborder
}
\renewcommand{\maketitle}{
  {\color{titlecolor}\thetitle\par}
  \vspace{0.6em}
}
\renewcommand{\KeywordTok}[1]{\textcolor{codekeyword}{\textbf{{#1}}}}
\renewcommand{\StringTok}[1]{\textcolor{codestring}{{#1}}}
\renewcommand{\CommentTok}[1]{\textcolor{codecomment}{\textit{{#1}}}}
\renewcommand{\FunctionTok}[1]{\textcolor{codefunction}{{#1}}}
\renewcommand{\DecValTok}[1]{\textcolor{codenumber}{{#1}}}
\renewcommand{\FloatTok}[1]{\textcolor{codenumber}{{#1}}}
""".strip()


UNICODE_REPLACEMENTS = {
    "🧠": "Brain",
    "🦥": "[Unsloth]",
    "█": "#",
}


def style_tex(content: str) -> str:
    for source, target in UNICODE_REPLACEMENTS.items():
        content = content.replace(source, target)

    content = content.replace(
        r"\title{qwen3\_4b\_lora\_finetune}",
        r"\title{Qwen3-4B-Instruct LoRA Fine-Tuning}",
    )

    content = content.replace(
        r"\usepackage[breakable]{tcolorbox}",
        r"\usepackage{tcolorbox}",
        1,
    )

    content = content.replace(
        r"\geometry{verbose,tmargin=1in,bmargin=1in,lmargin=1in,rmargin=1in}",
        STYLE_BLOCK,
        1,
    )

    content = content.replace(
        r"\section{Brain Qwen3-4B-Instruct LoRA" + "\n" + r"Fine-Tuning}\label{qwen3-4b-instruct-lora-fine-tuning}",
        r"\section{Qwen3-4B-Instruct LoRA Fine-Tuning}\label{qwen3-4b-instruct-lora-fine-tuning}",
    )

    content = content.replace(
        r"\DefineVerbatimEnvironment{Highlighting}{Verbatim}{commandchars=\\\{\}}",
        r"\DefineVerbatimEnvironment{Highlighting}{Verbatim}{commandchars=\\\{\},fontsize=\small}",
    )

    content = content.replace(
        r"\begin{tcolorbox}[breakable, size=fbox, boxrule=1pt, pad at break*=1mm,colback=cellbackground, colframe=cellborder]",
        r"\begin{tcolorbox}[size=fbox, pad at break*=1mm]",
    )

    content = content.replace(
        r"\prompt{In}{incolor}{",
        r"\prompt{In}{incolor}{",
    )

    return content


def main() -> int:
    if len(sys.argv) != 2:
        print("Usage: python scripts/style_notebook_latex.py path/to/notebook.tex")
        return 1

    path = Path(sys.argv[1])
    content = path.read_text(encoding="utf-8")
    styled = style_tex(content)
    path.write_text(styled, encoding="utf-8")
    print(f"Styled LaTeX written to {path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
